package com.jestron.kayit.command;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Komutlari secilen dile gore (lang/<dil>.yml -> commands) dinamik olarak kaydeder.
 * Spigot/Paper 1.16 - 1.21 arasinda CommandMap uzerinden calisir; kapanista/yenilemede temizler.
 */
public final class CommandRegistry {

    public static final String FALLBACK_PREFIX = "kayitplugin";

    private static final Map<String, String> DEFAULT_NAMES = new HashMap<String, String>();
    private static final Map<String, String> DEFAULT_SUBS = new HashMap<String, String>();

    static {
        DEFAULT_NAMES.put("register", "kayitol");
        DEFAULT_NAMES.put("login", "giris");
        DEFAULT_NAMES.put("code", "kod");
        DEFAULT_NAMES.put("email", "eposta");
        DEFAULT_NAMES.put("changepassword", "sifredegistir");
        DEFAULT_NAMES.put("forcelogin", "engelsizgiris");
        DEFAULT_NAMES.put("resetpassword", "sifresifirla");
        DEFAULT_NAMES.put("resetemail", "epostasifirla");
        DEFAULT_NAMES.put("alts", "hesaplar");
        DEFAULT_NAMES.put("admin", "kayitsistemi");
        DEFAULT_SUBS.put("code.resend", "gonder");
        DEFAULT_SUBS.put("email.add", "bagla");
        DEFAULT_SUBS.put("email.remove", "kaldir");
        DEFAULT_SUBS.put("email.change", "degistir");
        DEFAULT_SUBS.put("admin.reload", "yenile");
        DEFAULT_SUBS.put("admin.info", "bilgi");
        DEFAULT_SUBS.put("admin.testmail", "testmail");
    }

    private final KayitPlugin plugin;
    private final List<KCommand> registered = new ArrayList<KCommand>();
    private final Set<String> allowedBeforeLogin = ConcurrentHashMap.newKeySet();
    private CommandMap commandMap;

    public CommandRegistry(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- adlar

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        String out = s.trim().toLowerCase(Locale.ROOT);
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        return out.replace(" ", "");
    }

    /** Komutun gecerli ana adi (secili dile gore). */
    public String label(String key) {
        String def = DEFAULT_NAMES.containsKey(key) ? DEFAULT_NAMES.get(key) : key;
        String v = clean(plugin.lang().rawOr("commands." + key + ".name", def));
        return v.isEmpty() ? def : v;
    }

    public List<String> aliases(String key) {
        List<String> out = new ArrayList<String>();
        String name = label(key);
        for (String a : plugin.lang().rawList("commands." + key + ".aliases")) {
            String c = clean(a);
            if (!c.isEmpty() && !c.equals(name) && !out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Alt komut kelimesi (gonder, bagla, yenile ...). */
    public String sub(String key, String sub) {
        String def = DEFAULT_SUBS.containsKey(key + "." + sub) ? DEFAULT_SUBS.get(key + "." + sub) : sub;
        String v = clean(plugin.lang().rawOr("commands." + key + ".sub-" + sub, def));
        return v.isEmpty() ? def : v;
    }

    // ---------------------------------------------------------------- kayit

    public void registerAll() {
        commandMap = findCommandMap();
        if (commandMap == null) {
            plugin.getLogger().severe("CommandMap not found - commands could not be registered!");
            return;
        }
        List<KCommand> commands = new ArrayList<KCommand>();
        commands.add(new RegisterCommand(plugin, this));
        commands.add(new LoginCommand(plugin, this));
        commands.add(new CodeCommand(plugin, this));
        commands.add(new EmailCommand(plugin, this));
        commands.add(new ChangePasswordCommand(plugin, this));
        commands.add(new ForceLoginCommand(plugin, this));
        commands.add(new ResetPasswordCommand(plugin, this));
        commands.add(new ResetEmailCommand(plugin, this));
        commands.add(new AltsCommand(plugin, this));
        commands.add(new AdminCommand(plugin, this));

        for (KCommand cmd : commands) {
            boolean primary = commandMap.register(FALLBACK_PREFIX, cmd);
            registered.add(cmd);
            if (!primary) {
                plugin.getLogger().warning(Text.strip(Text.replace(plugin.lang().raw("console.command-conflict"),
                        "command", cmd.getName())));
            }
        }

        allowedBeforeLogin.clear();
        for (String key : new String[]{"register", "login", "code"}) {
            List<String> labels = new ArrayList<String>();
            labels.add(label(key));
            labels.addAll(aliases(key));
            for (String l : labels) {
                allowedBeforeLogin.add(l);
                allowedBeforeLogin.add(FALLBACK_PREFIX + ":" + l);
            }
        }
        allowedBeforeLogin.addAll(plugin.settings().extraAllowedCommands);
    }

    public void unregisterAll() {
        if (commandMap == null) {
            registered.clear();
            return;
        }
        Map<String, Command> known = knownCommands();
        for (KCommand cmd : registered) {
            if (known != null) {
                try {
                    Iterator<Map.Entry<String, Command>> it = known.entrySet().iterator();
                    while (it.hasNext()) {
                        if (it.next().getValue() == cmd) {
                            it.remove();
                        }
                    }
                } catch (Throwable ignored) {
                    // yok say
                }
            }
            try {
                cmd.unregister(commandMap);
            } catch (Throwable ignored) {
                // yok say
            }
        }
        registered.clear();
        allowedBeforeLogin.clear();
    }

    /** Oyunculara guncel komut agacini gonderir (1.13+). Sadece eklenti calisirken cagrilir. */
    public void syncLater() {
        plugin.tasks().later(new Runnable() {
            @Override
            public void run() {
                syncNow();
            }
        }, 1L);
    }

    public static void syncNow() {
        try {
            Method m = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(Bukkit.getServer());
        } catch (Throwable ignored) {
            // 1.13 oncesi veya desteklenmiyor
        }
    }

    // ---------------------------------------------------------------- sorgular

    /** Giris yapmadan kullanilabilir komut mu? (etiket kucuk harf, / olmadan) */
    public boolean isAllowedBeforeLogin(String label) {
        return allowedBeforeLogin.contains(clean(label));
    }

    public Command findCommand(String label) {
        if (commandMap == null) {
            return null;
        }
        try {
            return commandMap.getCommand(clean(label));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Komutun sahibi olan eklenti adi (vanilla icin "minecraft"). */
    public String ownerOf(Command cmd, String usedLabel) {
        if (cmd instanceof PluginCommand) {
            return ((PluginCommand) cmd).getPlugin().getName();
        }
        String label = clean(usedLabel);
        int colon = label.indexOf(':');
        if (colon > 0) {
            return label.substring(0, colon);
        }
        Map<String, Command> known = knownCommands();
        if (known != null) {
            try {
                for (Map.Entry<String, Command> e : known.entrySet()) {
                    String k = e.getKey();
                    int c = k.indexOf(':');
                    if (c > 0 && e.getValue() == cmd) {
                        return k.substring(0, c);
                    }
                }
            } catch (Throwable ignored) {
                // es zamanli degisiklik: yok say
            }
        }
        return "minecraft";
    }

    public List<KCommand> registered() {
        return Collections.unmodifiableList(registered);
    }

    // ---------------------------------------------------------------- yansima

    private static CommandMap findCommandMap() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object o = m.invoke(Bukkit.getServer());
            return o instanceof CommandMap ? (CommandMap) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands() {
        if (commandMap == null) {
            return null;
        }
        try {
            Method m = commandMap.getClass().getMethod("getKnownCommands");
            Object o = m.invoke(commandMap);
            if (o instanceof Map) {
                return (Map<String, Command>) o;
            }
        } catch (Throwable ignored) {
            // alan uzerinden dene
        }
        Class<?> c = commandMap.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("knownCommands");
                f.setAccessible(true);
                Object o = f.get(commandMap);
                return o instanceof Map ? (Map<String, Command>) o : null;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
