package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.ban.BanProvider;
import com.jestron.kayit.core.Lang;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.mail.MailResult;
import com.jestron.kayit.util.Checks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** /kayitsistemi yenile | bilgi | testmail <e-posta> */
public final class AdminCommand extends KCommand {

    public AdminCommand(KayitPlugin plugin, CommandRegistry registry) {
        super(plugin, registry, "admin", "kayit.manage");
    }

    @Override
    protected void run(final CommandSender sender, String label, String[] args) {
        String reload = registry.sub("admin", "reload");
        String info = registry.sub("admin", "info");
        String testmail = registry.sub("admin", "testmail");
        if (args.length == 0) {
            usage(sender, label, "reload", reload, "info", info, "testmail", testmail);
            return;
        }
        String sub = args[0];
        if (sub.equalsIgnoreCase(reload) || sub.equalsIgnoreCase("reload")) {
            String oldDb = plugin.settings().dbType;
            long start = System.currentTimeMillis();
            try {
                plugin.reloadAll();
                plugin.lang().send(sender, "admin.reload.done", "ms", System.currentTimeMillis() - start);
                if (!oldDb.equals(plugin.settings().dbType)) {
                    plugin.lang().send(sender, "admin.reload.database-note");
                }
            } catch (Throwable t) {
                plugin.getLogger().severe("Reload failed: " + t);
                plugin.lang().send(sender, "admin.reload.failed", "error", String.valueOf(t.getMessage()));
            }
            return;
        }
        if (sub.equalsIgnoreCase(info) || sub.equalsIgnoreCase("info")) {
            sendInfo(sender);
            return;
        }
        if (sub.equalsIgnoreCase(testmail) && args.length == 2) {
            final String to = args[1];
            if (!Checks.isEmail(to)) {
                plugin.lang().send(sender, "email.invalid");
                return;
            }
            if (!plugin.mail().isReady()) {
                plugin.lang().send(sender, "admin.testmail.failed", "error",
                        plugin.lang().raw("mail-errors.not-configured"));
                return;
            }
            plugin.lang().send(sender, "admin.testmail.sending", "email", to);
            plugin.mail().sendTemplate(to, "test").thenAccept(new Consumer<MailResult>() {
                @Override
                public void accept(final MailResult r) {
                    plugin.tasks().sync(new Runnable() {
                        @Override
                        public void run() {
                            if (sender instanceof Player && !((Player) sender).isOnline()) {
                                return;
                            }
                            if (r.isSuccess()) {
                                plugin.lang().send(sender, "admin.testmail.success", "ms", r.getMillis());
                            } else {
                                plugin.lang().send(sender, "admin.testmail.failed", "error", r.getError());
                            }
                        }
                    });
                }
            });
            return;
        }
        usage(sender, label, "reload", reload, "info", info, "testmail", testmail);
    }

    private void sendInfo(CommandSender sender) {
        Lang lang = plugin.lang();
        Settings s = plugin.settings();

        String mail;
        if (!s.emailEnabled) {
            mail = lang.raw("admin.status.disabled");
        } else if (!s.isMailConfigured()) {
            mail = lang.raw("admin.status.not-configured");
        } else if (plugin.mail().getLastError() != null) {
            mail = lang.raw("admin.status.error").replace("{error}", plugin.mail().getLastError());
        } else {
            mail = lang.raw("admin.status.ok");
        }

        StringBuilder providers = new StringBuilder();
        for (BanProvider p : plugin.bans().providers()) {
            if (providers.length() > 0) {
                providers.append("&8, ");
            }
            String status;
            if (!p.isAvailable()) {
                status = lang.raw("admin.status.disabled");
            } else if (p.getLastError() != null) {
                status = lang.raw("admin.status.error").replace("{error}", p.getLastError());
            } else {
                status = lang.raw("admin.status.ok");
            }
            providers.append("&f").append(p.name()).append(' ').append(status);
        }

        long last = plugin.bans().getLastRefresh();
        String refresh = last <= 0 ? lang.raw("admin.status.never") : lang.date(s, last);

        lang.send(sender, "admin.info",
                "version", plugin.getDescription().getVersion(),
                "server_version", Bukkit.getVersion(),
                "language", lang.code(),
                "database", plugin.database().describe(),
                "email_status", mail,
                "online", Bukkit.getOnlinePlayers().size(),
                "logged", plugin.auth().loggedInCount(),
                "bans", plugin.bans().getActiveCount(),
                "refresh", refresh,
                "providers", providers.toString());
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList(registry.sub("admin", "reload"), registry.sub("admin", "info"),
                    registry.sub("admin", "testmail")), args[0]);
        }
        return Collections.emptyList();
    }
}
