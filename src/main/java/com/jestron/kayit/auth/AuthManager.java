package com.jestron.kayit.auth;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.core.Lang;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.mail.MailResult;
import com.jestron.kayit.storage.Database;
import com.jestron.kayit.storage.PlayerAccount;
import com.jestron.kayit.util.Checks;
import com.jestron.kayit.webhook.Webhook;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Kayit / giris / e-posta dogrulama mantigi.
 * Kurallar: veritabani + BCrypt + SMTP DAIMA arka planda; oyuncu durumu DAIMA ana thread'de degisir.
 */
public final class AuthManager {

    public static final String BYPASS_IP_LIMIT = "kayit.bypass.iplimit";
    private static final String RELOAD_FILE = ".reload-state.yml";

    private final KayitPlugin plugin;
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<UUID, AuthSession>();
    private final Map<UUID, Preload> preloads = new ConcurrentHashMap<UUID, Preload>();
    private BukkitTask ticker;

    private static final class Preload {
        final PlayerAccount account;
        final boolean sessionValid;
        final long time;

        Preload(PlayerAccount account, boolean sessionValid, long time) {
            this.account = account;
            this.sessionValid = sessionValid;
            this.time = time;
        }
    }

    public AuthManager(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Lang lang() {
        return plugin.lang();
    }

    private Database db() {
        return plugin.database();
    }

    // ================================================================ yasam dongusu

    public void start() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    /** Eklenti kapanirken: oturumlari kaydet, efektleri temizle. Ana thread. */
    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        long now = System.currentTimeMillis();
        Settings s = settings();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AuthSession session = sessions.get(p.getUniqueId());
            if (session == null) {
                continue;
            }
            removeRestrictions(p, session);
            if (session.isLoggedIn() && db() != null) {
                try {
                    db().recordQuit(session.getUuid(), now);
                    if (s.sessionEnabled) {
                        db().saveSession(session.getUuid(), session.getIp(), now + s.sessionTimeoutMinutes * 60000L);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not save session of " + session.getName() + ": " + e.getMessage());
                }
            }
        }
        sessions.clear();
        preloads.clear();
    }

    /** /plugman reload vb. sonrasi giris yapmis oyuncularin oturumunu korumak icin. */
    public void saveReloadState() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.set("time", System.currentTimeMillis());
            for (AuthSession s : sessions.values()) {
                if (s.isLoggedIn()) {
                    y.set("players." + s.getUuid(), s.getIp());
                }
            }
            File f = new File(plugin.getDataFolder(), RELOAD_FILE);
            y.save(f);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save reload state: " + e.getMessage());
        }
    }

    /** Eklenti acilirken zaten cevrimici olan oyuncular (reload) icin oturum olusturur. */
    public void restoreOnlinePlayers() {
        Map<String, String> restore = new HashMap<String, String>();
        File f = new File(plugin.getDataFolder(), RELOAD_FILE);
        if (f.exists()) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                long time = y.getLong("time", 0L);
                if (System.currentTimeMillis() - time <= 120000L && y.isConfigurationSection("players")) {
                    for (String key : y.getConfigurationSection("players").getKeys(false)) {
                        restore.put(key, y.getString("players." + key));
                    }
                }
            } catch (Exception ignored) {
                // bozuk dosya: yok say
            }
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            String ip = ip(p);
            String saved = restore.get(p.getUniqueId().toString());
            boolean keep = saved != null && saved.equals(ip);
            handleJoin(p, keep);
        }
    }

    // ================================================================ olaylar

    /** Asenkron on-giris: hesap verisini onceden yukler, cevrimici hesabin atilmasini engeller. */
    public void handlePreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        String name = event.getName();
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress() == null ? "unknown" : event.getAddress().getHostAddress();
        Settings s = settings();

        if (s.preventOnlineKick) {
            for (AuthSession other : sessions.values()) {
                if (other.isLoggedIn() && other.getName().equalsIgnoreCase(name)) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, lang().get("security.already-online"));
                    return;
                }
            }
        }
        try {
            long now = System.currentTimeMillis();
            PlayerAccount acc = db().getAccount(uuid);
            boolean valid = s.sessionEnabled && acc != null && acc.isRegistered() && db().hasValidSession(uuid, ip, now);
            preloads.put(uuid, new Preload(acc, valid, now));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not preload account of " + name + ": " + e.getMessage());
        }
    }

    public void handleJoin(final Player p, final boolean restoreLogin) {
        final UUID uuid = p.getUniqueId();
        final long now = System.currentTimeMillis();
        final AuthSession session = new AuthSession(uuid, p.getName(), ip(p), now);
        sessions.put(uuid, session);

        Preload pre = preloads.remove(uuid);
        if (pre != null && now - pre.time < 60000L) {
            apply(p, session, pre.account, restoreLogin || pre.sessionValid,
                    restoreLogin ? "login.reload-restored" : "login.session-restored");
        } else {
            load(p, session, restoreLogin);
        }
    }

    private void load(final Player p, final AuthSession session, final boolean restoreLogin) {
        if (!session.loading().compareAndSet(false, true)) {
            return;
        }
        session.setLastLoadAttempt(System.currentTimeMillis());
        final UUID uuid = session.getUuid();
        final String ip = session.getIp();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerAccount acc = db().getAccount(uuid);
                    boolean valid = settings().sessionEnabled && acc != null && acc.isRegistered()
                            && db().hasValidSession(uuid, ip, System.currentTimeMillis());
                    final PlayerAccount fAcc = acc;
                    final boolean auto = restoreLogin || valid;
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            session.loading().set(false);
                            apply(p, session, fAcc, auto,
                                    restoreLogin ? "login.reload-restored" : "login.session-restored");
                        }
                    });
                } catch (Exception e) {
                    session.loading().set(false);
                    plugin.getLogger().log(Level.WARNING, "Could not load account of " + session.getName()
                            + " (will retry): " + e.getMessage());
                }
            }
        });
    }

    private void apply(Player p, AuthSession session, PlayerAccount acc, boolean autoLogin, String autoMessage) {
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != session) {
            return;
        }
        session.setAccount(acc);
        session.setLoaded(true);
        if (autoLogin && acc != null && acc.isRegistered()) {
            completeLogin(p, session, autoMessage);
            return;
        }
        applyRestrictions(p, session);
        session.setLastReminder(System.currentTimeMillis());
        prompt(p, session, true);
        plugin.alts().checkPlayer(p);
    }

    public void handleQuit(Player p) {
        UUID uuid = p.getUniqueId();
        preloads.remove(uuid);
        final AuthSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        removeRestrictions(p, session);
        if (session.isLoggedIn()) {
            final long now = System.currentTimeMillis();
            final boolean saveSession = settings().sessionEnabled;
            final long expires = now + settings().sessionTimeoutMinutes * 60000L;
            plugin.tasks().async(new Runnable() {
                @Override
                public void run() {
                    try {
                        db().recordQuit(session.getUuid(), now);
                        if (saveSession) {
                            db().saveSession(session.getUuid(), session.getIp(), expires);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not record quit of " + session.getName() + ": "
                                + e.getMessage());
                    }
                }
            });
        }
    }

    /** Saniyede bir: kod suresi, zaman asimi, hatirlatma, korluk, yeniden yukleme. */
    private void tick() {
        long now = System.currentTimeMillis();
        Settings s = settings();
        Iterator<Map.Entry<UUID, Preload>> it = preloads.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().time > 60000L) {
                it.remove();
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            AuthSession session = sessions.get(p.getUniqueId());
            if (session == null) {
                continue;
            }
            PendingAction pa = session.getPending();
            if (pa != null && pa.isExpired(now)) {
                session.setPending(null);
                lang().send(p, "code.expired");
                if (!session.isLoggedIn()) {
                    session.setLastReminder(now);
                    prompt(p, session, s.titles);
                }
            }
            if (session.isLoggedIn()) {
                continue;
            }
            if (!session.isLoaded()) {
                if (!session.loading().get() && now - session.getLastLoadAttempt() > 5000L) {
                    load(p, session, false);
                }
            }
            if (s.loginTimeoutSeconds > 0 && now - session.getJoinTime() > s.loginTimeoutSeconds * 1000L
                    && session.getPending() == null && !session.busy().get()) {
                kick(p, "security.timeout-kick");
                continue;
            }
            if (session.isLoaded() && now - session.getLastReminder() >= s.reminderIntervalSeconds * 1000L) {
                session.setLastReminder(now);
                prompt(p, session, s.titles);
            }
            if (s.blindness && !p.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                applyRestrictions(p, session);
            }
        }
    }

    // ================================================================ komutlar: kayit / giris

    public void register(final Player p, final String password, final String rawEmail) {
        final AuthSession session = ready(p);
        if (session == null) {
            return;
        }
        if (session.isRegistered()) {
            lang().send(p, "register.already", labels());
            return;
        }
        if (!validatePassword(p, password)) {
            return;
        }
        final String email = rawEmail == null ? null : rawEmail.trim();
        if (email != null && !Checks.isEmail(email)) {
            lang().send(p, "email.invalid");
            return;
        }
        PendingAction old = session.getPending();
        if (old != null && old.getType() == PendingAction.Type.REGISTER) {
            session.setPending(null); // yeni kayit denemesi eskisini iptal eder
        }
        if (!session.busy().compareAndSet(false, true)) {
            lang().send(p, "general.please-wait");
            return;
        }
        final Settings s = settings();
        final boolean bypass = p.hasPermission(BYPASS_IP_LIMIT);
        final UUID uuid = p.getUniqueId();
        final String name = p.getName();
        final String ip = session.getIp();
        final boolean mailReady = plugin.mail().isReady();

        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!bypass && s.maxRegistrationsPerIp > 0
                            && db().countRegistrationsByIp(ip, uuid) >= s.maxRegistrationsPerIp) {
                        message(p, "register.ip-limit", "max", s.maxRegistrationsPerIp);
                        return;
                    }
                    if (email != null && s.maxAccountsPerEmail > 0
                            && db().countAccountsByEmail(email, uuid) >= s.maxAccountsPerEmail) {
                        message(p, "register.email-limit", "max", s.maxAccountsPerEmail);
                        return;
                    }
                    final String hash = BCrypt.hashpw(password, BCrypt.gensalt(s.bcryptCost));
                    if (email != null && mailReady) {
                        plugin.tasks().sync(new Runnable() {
                            @Override
                            public void run() {
                                if (!valid(p, session)) {
                                    return;
                                }
                                PendingAction pa = newPending(PendingAction.Type.REGISTER, hash, email, null);
                                session.setPending(pa);
                                lang().send(p, "register.code-sent", merge(labels(), "email", mask(email),
                                        "seconds", s.codeExpirySeconds));
                                title(p, "title.code");
                                sendCode(p, session, pa);
                            }
                        });
                        return;
                    }
                    db().saveRegistration(uuid, name, hash, email, false, ip, System.currentTimeMillis());
                    final PlayerAccount acc = db().getAccount(uuid);
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (!valid(p, session)) {
                                return;
                            }
                            session.setAccount(acc);
                            if (email != null) {
                                lang().send(p, "register.email-saved-unverified");
                            }
                            finishRegistration(p, session);
                        }
                    });
                } catch (Exception e) {
                    fail(p, "register", e);
                } finally {
                    release(session);
                }
            }
        });
    }

    private void finishRegistration(Player p, AuthSession session) {
        completeLogin(p, session, "register.success");
        if (settings().webhookRegister) {
            plugin.webhook().send("Kayit / Register", p.getName() + " (" + p.getUniqueId() + ")", Webhook.COLOR_OK);
        }
    }

    public void login(final Player p, final String password) {
        final AuthSession session = ready(p);
        if (session == null) {
            return;
        }
        if (session.isLoggedIn()) {
            lang().send(p, "login.already");
            return;
        }
        if (!session.isRegistered()) {
            lang().send(p, "login.not-registered", labels());
            return;
        }
        PendingAction pa = session.getPending();
        if (pa != null && pa.getType() == PendingAction.Type.LOGIN) {
            lang().send(p, "code.pending", labels());
            return;
        }
        Settings s = settings();
        if (s.maxSessionsPerIp > 0 && !p.hasPermission(BYPASS_IP_LIMIT)
                && countLoggedIn(session.getIp(), p.getUniqueId()) >= s.maxSessionsPerIp) {
            lang().send(p, "login.ip-limit", "max", s.maxSessionsPerIp);
            return;
        }
        if (!session.busy().compareAndSet(false, true)) {
            lang().send(p, "general.please-wait");
            return;
        }
        final String hash = session.getAccount().getPasswordHash();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                boolean ok;
                try {
                    ok = BCrypt.checkpw(password, hash);
                } catch (Exception e) {
                    ok = false;
                }
                final boolean correct = ok;
                plugin.tasks().sync(new Runnable() {
                    @Override
                    public void run() {
                        session.busy().set(false);
                        if (!valid(p, session) || session.isLoggedIn()) {
                            return;
                        }
                        if (!correct) {
                            wrongPassword(p, session);
                        } else {
                            afterPassword(p, session);
                        }
                    }
                });
            }
        });
    }

    private void wrongPassword(Player p, AuthSession session) {
        int max = settings().maxLoginAttempts;
        int failed = session.addFailedAttempt();
        if (max <= 0) {
            lang().send(p, "login.wrong-password-unlimited");
            return;
        }
        if (failed >= max) {
            kick(p, "security.attempts-kick");
            return;
        }
        lang().send(p, "login.wrong-password", "remaining", max - failed);
    }

    private void afterPassword(Player p, AuthSession session) {
        PlayerAccount acc = session.getAccount();
        Settings s = settings();
        if (s.requireCodeOnLogin && acc != null && acc.isEmailVerified() && plugin.mail().isReady()) {
            PendingAction pa = newPending(PendingAction.Type.LOGIN, null, acc.getEmail(), null);
            session.setPending(pa);
            lang().send(p, "login.code-sent", merge(labels(), "email", mask(acc.getEmail())));
            title(p, "title.code");
            sendCode(p, session, pa);
            return;
        }
        completeLogin(p, session, "login.success");
    }

    // ================================================================ kod

    public void code(final Player p, String input) {
        final AuthSession session = session(p);
        if (session == null) {
            return;
        }
        final PendingAction pa = session.getPending();
        if (pa == null) {
            lang().send(p, "code.no-pending");
            return;
        }
        long now = System.currentTimeMillis();
        if (pa.isExpired(now)) {
            session.setPending(null);
            lang().send(p, "code.expired");
            return;
        }
        if (!Checks.safeEquals(pa.getCode(), input == null ? "" : input.trim())) {
            int attempts = pa.addAttempt();
            int left = settings().maxCodeAttempts - attempts;
            if (left <= 0) {
                session.setPending(null);
                lang().send(p, "code.too-many");
                if (!session.isLoggedIn()) {
                    prompt(p, session, settings().titles);
                }
            } else {
                lang().send(p, "code.wrong", "remaining", left);
            }
            return;
        }
        session.setPending(null);
        switch (pa.getType()) {
            case LOGIN:
                completeLogin(p, session, "login.success");
                return;
            case REGISTER:
                completeRegistrationWithEmail(p, session, pa);
                return;
            default:
                completeEmailAction(p, session, pa);
        }
    }

    private void completeRegistrationWithEmail(final Player p, final AuthSession session, final PendingAction pa) {
        if (!session.busy().compareAndSet(false, true)) {
            session.setPending(pa);
            lang().send(p, "general.please-wait");
            return;
        }
        final UUID uuid = p.getUniqueId();
        final String name = p.getName();
        final String ip = session.getIp();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    db().saveRegistration(uuid, name, pa.getPasswordHash(), pa.getTargetEmail(), true, ip,
                            System.currentTimeMillis());
                    final PlayerAccount acc = db().getAccount(uuid);
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (!valid(p, session)) {
                                return;
                            }
                            session.setAccount(acc);
                            lang().send(p, "code.verified");
                            finishRegistration(p, session);
                        }
                    });
                } catch (Exception e) {
                    fail(p, "register", e);
                } finally {
                    release(session);
                }
            }
        });
    }

    private void completeEmailAction(final Player p, final AuthSession session, final PendingAction pa) {
        final UUID uuid = p.getUniqueId();
        final String name = p.getName();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    final String key;
                    if (pa.getType() == PendingAction.Type.EMAIL_REMOVE) {
                        db().setEmail(uuid, null, false);
                        key = "email.removed";
                    } else {
                        db().setEmail(uuid, pa.getTargetEmail(), true);
                        key = pa.getType() == PendingAction.Type.EMAIL_ADD ? "email.linked" : "email.changed";
                        if (pa.getType() == PendingAction.Type.EMAIL_CHANGE && pa.getOldEmail() != null) {
                            plugin.mail().sendTemplate(pa.getOldEmail(), "email-changed-notify", "player", name,
                                    "new_email", pa.getTargetEmail(), "email", pa.getOldEmail());
                        }
                    }
                    final PlayerAccount acc = db().getAccount(uuid);
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (!valid(p, session)) {
                                return;
                            }
                            session.setAccount(acc);
                            lang().send(p, key, "email", pa.getTargetEmail());
                        }
                    });
                } catch (Exception e) {
                    fail(p, "email", e);
                }
            }
        });
    }

    public void resend(Player p) {
        AuthSession session = session(p);
        if (session == null) {
            return;
        }
        PendingAction pa = session.getPending();
        long now = System.currentTimeMillis();
        if (pa == null || pa.isExpired(now)) {
            session.setPending(null);
            lang().send(p, "code.no-pending");
            return;
        }
        long wait = settings().resendCooldownSeconds * 1000L - (now - pa.getLastSent());
        if (wait > 0) {
            lang().send(p, "code.cooldown", "seconds", (wait + 999) / 1000);
            return;
        }
        pa.renew(Checks.randomDigits(settings().codeLength), now + settings().codeExpirySeconds * 1000L);
        sendCode(p, session, pa);
        lang().send(p, "code.resent", "email", mask(pa.getTargetEmail()));
    }

    private PendingAction newPending(PendingAction.Type type, String hash, String target, String old) {
        Settings s = settings();
        return new PendingAction(type, Checks.randomDigits(s.codeLength),
                System.currentTimeMillis() + s.codeExpirySeconds * 1000L, hash, target, old);
    }

    private void sendCode(final Player p, final AuthSession session, final PendingAction pa) {
        pa.markSent(System.currentTimeMillis());
        plugin.mail().sendTemplate(pa.getTargetEmail(), pa.getType().template(), "player", p.getName(),
                "code", pa.getCode(), "email", pa.getTargetEmail(), "new_email", pa.getTargetEmail())
                .thenAccept(new Consumer<MailResult>() {
                    @Override
                    public void accept(final MailResult r) {
                        if (r.isSuccess()) {
                            return;
                        }
                        plugin.tasks().sync(new Runnable() {
                            @Override
                            public void run() {
                                if (!valid(p, session)) {
                                    return;
                                }
                                lang().send(p, "email.send-failed");
                                if (p.hasPermission("kayit.manage")) {
                                    p.sendMessage(lang().format("{prefix}&7" + r.getError()));
                                }
                                // Sifre dogru girildi ama SMTP calismiyor: oyuncu kilitli kalmasin
                                if (pa.getType() == PendingAction.Type.LOGIN && session.getPending() == pa) {
                                    session.setPending(null);
                                    completeLogin(p, session, "login.success");
                                }
                            }
                        });
                    }
                });
    }

    // ================================================================ e-posta komutlari

    public void emailAdd(final Player p, final String label, String rawEmail) {
        final AuthSession session = loggedSession(p);
        if (session == null) {
            return;
        }
        if (session.getPending() != null) {
            lang().send(p, "code.pending", labels());
            return;
        }
        final String email = rawEmail.trim();
        if (!Checks.isEmail(email)) {
            lang().send(p, "email.invalid");
            return;
        }
        PlayerAccount acc = session.getAccount();
        if (acc != null && acc.hasEmail()) {
            lang().send(p, "email.already-linked", merge(emailLabels(label)));
            return;
        }
        startEmailAction(p, session, PendingAction.Type.EMAIL_ADD, email, null);
    }

    public void emailRemove(final Player p, String label) {
        final AuthSession session = loggedSession(p);
        if (session == null) {
            return;
        }
        if (session.getPending() != null) {
            lang().send(p, "code.pending", labels());
            return;
        }
        PlayerAccount acc = session.getAccount();
        if (acc == null || !acc.hasEmail()) {
            lang().send(p, "email.not-linked", merge(emailLabels(label)));
            return;
        }
        if (acc.isEmailVerified() && plugin.mail().isReady()) {
            PendingAction pa = newPending(PendingAction.Type.EMAIL_REMOVE, null, acc.getEmail(), null);
            session.setPending(pa);
            lang().send(p, "email.code-sent", merge(labels(), "email", mask(acc.getEmail())));
            sendCode(p, session, pa);
            return;
        }
        completeEmailAction(p, session, new PendingAction(PendingAction.Type.EMAIL_REMOVE, "", 0L, null, null, null));
    }

    public void emailChange(final Player p, String label, String oldEmail, String rawNew) {
        final AuthSession session = loggedSession(p);
        if (session == null) {
            return;
        }
        if (session.getPending() != null) {
            lang().send(p, "code.pending", labels());
            return;
        }
        PlayerAccount acc = session.getAccount();
        if (acc == null || !acc.hasEmail()) {
            lang().send(p, "email.not-linked", merge(emailLabels(label)));
            return;
        }
        if (!acc.getEmail().equalsIgnoreCase(oldEmail.trim())) {
            lang().send(p, "email.mismatch");
            return;
        }
        String newEmail = rawNew.trim();
        if (!Checks.isEmail(newEmail)) {
            lang().send(p, "email.invalid");
            return;
        }
        if (newEmail.equalsIgnoreCase(acc.getEmail())) {
            lang().send(p, "email.same");
            return;
        }
        startEmailAction(p, session, PendingAction.Type.EMAIL_CHANGE, newEmail, acc.getEmail());
    }

    private void startEmailAction(final Player p, final AuthSession session, final PendingAction.Type type,
                                  final String target, final String old) {
        if (!session.busy().compareAndSet(false, true)) {
            lang().send(p, "general.please-wait");
            return;
        }
        final Settings s = settings();
        final UUID uuid = p.getUniqueId();
        final boolean mailReady = plugin.mail().isReady();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    if (s.maxAccountsPerEmail > 0 && db().countAccountsByEmail(target, uuid) >= s.maxAccountsPerEmail) {
                        message(p, "register.email-limit", "max", s.maxAccountsPerEmail);
                        return;
                    }
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (!valid(p, session)) {
                                return;
                            }
                            if (mailReady) {
                                PendingAction pa = newPending(type, null, target, old);
                                session.setPending(pa);
                                lang().send(p, "email.code-sent", merge(labels(), "email", mask(target)));
                                sendCode(p, session, pa);
                            } else {
                                completeEmailAction(p, session, new PendingAction(type, "", 0L, null, target, old));
                            }
                        }
                    });
                } catch (Exception e) {
                    fail(p, "email", e);
                } finally {
                    release(session);
                }
            }
        });
    }

    // ================================================================ sifre degistirme

    public void changePassword(final Player p, final String oldPassword, final String newPassword) {
        final AuthSession session = loggedSession(p);
        if (session == null) {
            return;
        }
        if (!session.isRegistered()) {
            lang().send(p, "login.not-registered", labels());
            return;
        }
        if (oldPassword.equals(newPassword)) {
            lang().send(p, "changepassword.same");
            return;
        }
        if (!validatePassword(p, newPassword)) {
            return;
        }
        if (!session.busy().compareAndSet(false, true)) {
            lang().send(p, "general.please-wait");
            return;
        }
        final String hash = session.getAccount().getPasswordHash();
        final UUID uuid = p.getUniqueId();
        final int cost = settings().bcryptCost;
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean ok;
                    try {
                        ok = BCrypt.checkpw(oldPassword, hash);
                    } catch (Exception e) {
                        ok = false;
                    }
                    if (!ok) {
                        message(p, "changepassword.wrong-old");
                        return;
                    }
                    db().setPassword(uuid, BCrypt.hashpw(newPassword, BCrypt.gensalt(cost)));
                    db().deleteSession(uuid);
                    final PlayerAccount acc = db().getAccount(uuid);
                    if (acc != null && acc.isEmailVerified()) {
                        plugin.mail().sendTemplate(acc.getEmail(), "password-changed", "player", acc.getName());
                    }
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (!valid(p, session)) {
                                return;
                            }
                            session.setAccount(acc);
                            lang().send(p, "changepassword.success");
                        }
                    });
                } catch (Exception e) {
                    fail(p, "changepassword", e);
                } finally {
                    release(session);
                }
            }
        });
    }

    // ================================================================ yonetici

    public void forceLogin(CommandSender sender, Player target) {
        AuthSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            lang().send(sender, "general.player-not-found", "player", target.getName());
            return;
        }
        if (session.isLoggedIn()) {
            lang().send(sender, "admin.forcelogin.already", "player", target.getName());
            return;
        }
        session.setPending(null);
        completeLogin(target, session, "admin.forcelogin.target");
        lang().send(sender, "admin.forcelogin.done", "player", target.getName());
        adminLog(sender, "forcelogin", target.getName());
    }

    public void resetPassword(final CommandSender sender, final String name) {
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerAccount acc = db().getAccountByName(name);
                    if (acc == null || !acc.isRegistered()) {
                        message(sender, "general.player-not-registered", "player", name);
                        return;
                    }
                    final UUID uuid = acc.getUuid();
                    db().setPassword(uuid, "");
                    db().deleteSession(uuid);
                    if (acc.isEmailVerified()) {
                        plugin.mail().sendTemplate(acc.getEmail(), "admin-reset-password", "player", acc.getName());
                    }
                    final PlayerAccount fresh = db().getAccount(uuid);
                    final String realName = acc.getName();
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            lang().send(sender, "admin.resetpassword.done", "player", realName);
                            adminLog(sender, "resetpassword", realName);
                            Player t = Bukkit.getPlayer(uuid);
                            AuthSession s = t == null ? null : sessions.get(uuid);
                            if (t == null || s == null) {
                                return;
                            }
                            s.setAccount(fresh);
                            s.setPending(null);
                            s.resetFailedAttempts();
                            s.setJoinTime(System.currentTimeMillis());
                            s.setLastReminder(System.currentTimeMillis());
                            if (s.isLoggedIn()) {
                                s.setLoggedIn(false);
                                applyRestrictions(t, s);
                            }
                            if (!lang().isEmpty("security.reset-kick")) {
                                kick(t, "security.reset-kick");
                                return;
                            }
                            lang().send(t, "admin.resetpassword.target", labels());
                            prompt(t, s, settings().titles);
                        }
                    });
                } catch (Exception e) {
                    fail(sender, "resetpassword", e);
                }
            }
        });
    }

    public void resetEmail(final CommandSender sender, final String name) {
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerAccount acc = db().getAccountByName(name);
                    if (acc == null) {
                        message(sender, "general.player-not-registered", "player", name);
                        return;
                    }
                    if (!acc.hasEmail()) {
                        message(sender, "admin.resetemail.none", "player", acc.getName());
                        return;
                    }
                    final UUID uuid = acc.getUuid();
                    String old = acc.getEmail();
                    db().setEmail(uuid, null, false);
                    plugin.mail().sendTemplate(old, "admin-reset-email", "player", acc.getName());
                    final PlayerAccount fresh = db().getAccount(uuid);
                    final String realName = acc.getName();
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            lang().send(sender, "admin.resetemail.done", "player", realName);
                            adminLog(sender, "resetemail", realName);
                            AuthSession s = sessions.get(uuid);
                            if (s != null) {
                                s.setAccount(fresh);
                            }
                        }
                    });
                } catch (Exception e) {
                    fail(sender, "resetemail", e);
                }
            }
        });
    }

    private void adminLog(CommandSender sender, String action, String target) {
        if (settings().webhookAdmin) {
            plugin.webhook().send("Admin: " + action, sender.getName() + " -> " + target, Webhook.COLOR_WARN);
        }
    }

    // ================================================================ ortak

    private void completeLogin(Player p, AuthSession session, String messageKey) {
        session.setLoggedIn(true);
        session.resetFailedAttempts();
        session.setPending(null);
        removeRestrictions(p, session);
        if (messageKey != null) {
            lang().send(p, messageKey, merge(labels(), "player", p.getName()));
        }
        final UUID uuid = p.getUniqueId();
        final String name = p.getName();
        final String ip = session.getIp();
        final long now = System.currentTimeMillis();
        plugin.tasks().async(new Runnable() {
            @Override
            public void run() {
                try {
                    db().recordLogin(uuid, name, ip, now);
                    db().deleteSession(uuid);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not record login of " + name + ": " + e.getMessage());
                }
            }
        });
        if (settings().webhookLogin) {
            plugin.webhook().send("Giris / Login", name, Webhook.COLOR_INFO);
        }
        plugin.alts().checkPlayer(p);
    }

    private void prompt(Player p, AuthSession session, boolean withTitle) {
        Object[] l = labels();
        String key;
        if (session.getPending() != null) {
            key = "code";
        } else if (session.isRegistered()) {
            key = "login";
        } else {
            key = "register";
        }
        lang().send(p, "prompt." + key, l);
        if (withTitle && settings().titles) {
            title(p, "title." + key);
        }
    }

    private void title(Player p, String base) {
        if (!settings().titles) {
            return;
        }
        try {
            Object[] l = labels();
            p.sendTitle(lang().get(base + ".title", l), lang().get(base + ".subtitle", l), 10, 60, 10);
        } catch (Throwable ignored) {
            // cok eski istemci / desteklenmeyen
        }
    }

    private void applyRestrictions(Player p, AuthSession session) {
        Settings s = settings();
        if (!s.blindness) {
            return;
        }
        int seconds = s.loginTimeoutSeconds > 0 ? s.loginTimeoutSeconds + 30 : 3600;
        try {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, seconds * 20, 1, false, false));
            session.setBlinded(true);
        } catch (Throwable ignored) {
            // yok say
        }
    }

    private void removeRestrictions(Player p, AuthSession session) {
        if (session.isBlinded()) {
            try {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            } catch (Throwable ignored) {
                // yok say
            }
            session.setBlinded(false);
        }
        if (settings().titles) {
            try {
                p.resetTitle();
            } catch (Throwable ignored) {
                // yok say
            }
        }
    }

    private void kick(Player p, String key) {
        String msg = lang().get(key, labels());
        try {
            p.kickPlayer(msg);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not kick " + p.getName() + ": " + t.getMessage());
        }
    }

    private boolean validatePassword(Player p, String password) {
        Settings s = settings();
        if (password == null || password.length() < s.passwordMin) {
            lang().send(p, "password.too-short", "min", s.passwordMin, "max", s.passwordMax);
            return false;
        }
        if (password.length() > s.passwordMax) {
            lang().send(p, "password.too-long", "min", s.passwordMin, "max", s.passwordMax);
            return false;
        }
        if (s.disallowUsername && password.equalsIgnoreCase(p.getName())) {
            lang().send(p, "password.same-as-name");
            return false;
        }
        if (s.blockedPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            lang().send(p, "password.blocked");
            return false;
        }
        return true;
    }

    /** Kayit/giris icin hazir oturum (yuklenmis). */
    private AuthSession ready(Player p) {
        AuthSession session = session(p);
        if (session == null) {
            return null;
        }
        if (!session.isLoaded()) {
            lang().send(p, "general.loading");
            return null;
        }
        return session;
    }

    private AuthSession loggedSession(Player p) {
        AuthSession session = session(p);
        if (session == null) {
            return null;
        }
        if (!session.isLoggedIn()) {
            lang().send(p, "general.must-login");
            return null;
        }
        return session;
    }

    private AuthSession session(Player p) {
        AuthSession session = sessions.get(p.getUniqueId());
        if (session == null) {
            // NPC / sahte oyuncu veya henuz olusmamis oturum
            lang().send(p, "general.internal-error");
        }
        return session;
    }

    private boolean valid(Player p, AuthSession session) {
        return p.isOnline() && sessions.get(p.getUniqueId()) == session;
    }

    private void release(final AuthSession session) {
        plugin.tasks().sync(new Runnable() {
            @Override
            public void run() {
                session.busy().set(false);
            }
        });
    }

    private void message(final CommandSender to, final String key, final Object... kv) {
        plugin.tasks().sync(new Runnable() {
            @Override
            public void run() {
                if (to instanceof Player && !((Player) to).isOnline()) {
                    return;
                }
                lang().send(to, key, kv);
            }
        });
    }

    private void fail(CommandSender to, String action, Exception e) {
        plugin.getLogger().log(Level.SEVERE, "Error during " + action + " for " + to.getName(), e);
        message(to, "general.internal-error");
    }

    private int countLoggedIn(String ip, UUID exclude) {
        int n = 0;
        for (AuthSession s : sessions.values()) {
            if (s.isLoggedIn() && !s.getUuid().equals(exclude) && s.getIp().equals(ip)) {
                n++;
            }
        }
        return n;
    }

    /** Mesajlarda kullanilan komut adlari: {register} {login} {code} {resend} */
    public Object[] labels() {
        return new Object[]{
                "register", plugin.commands().label("register"),
                "login", plugin.commands().label("login"),
                "code", plugin.commands().label("code"),
                "resend", plugin.commands().sub("code", "resend")
        };
    }

    private Object[] emailLabels(String label) {
        return new Object[]{
                "label", label,
                "add", plugin.commands().sub("email", "add"),
                "remove", plugin.commands().sub("email", "remove"),
                "change", plugin.commands().sub("email", "change")
        };
    }

    private static Object[] merge(Object[] a, Object... b) {
        Object[] out = new Object[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** jestron@gmail.com -> je*****@gmail.com */
    public static String mask(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        int keep = Math.min(2, local.length());
        StringBuilder sb = new StringBuilder(local.substring(0, keep));
        for (int i = keep; i < Math.max(keep + 3, local.length()); i++) {
            sb.append('*');
        }
        return sb.append(email.substring(at)).toString();
    }

    private static String ip(Player p) {
        try {
            return p.getAddress() == null || p.getAddress().getAddress() == null
                    ? "unknown" : p.getAddress().getAddress().getHostAddress();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    // ================================================================ sorgular

    /** Giris yapmamis gercek oyuncu mu? (NPC'ler ve oturumu olmayanlar kisitlanmaz) */
    public boolean isRestricted(Player p) {
        AuthSession s = sessions.get(p.getUniqueId());
        return s != null && !s.isLoggedIn();
    }

    public boolean isLoggedIn(UUID uuid) {
        AuthSession s = sessions.get(uuid);
        return s != null && s.isLoggedIn();
    }

    public AuthSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public Map<UUID, AuthSession> sessions() {
        return Collections.unmodifiableMap(sessions);
    }

    public int loggedInCount() {
        int n = 0;
        for (AuthSession s : sessions.values()) {
            if (s.isLoggedIn()) {
                n++;
            }
        }
        return n;
    }
}
