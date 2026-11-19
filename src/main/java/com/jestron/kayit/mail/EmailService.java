package com.jestron.kayit.mail;

import com.jestron.kayit.KayitPlugin;
import com.jestron.kayit.core.Settings;
import com.jestron.kayit.util.Text;
import com.sun.mail.smtp.SMTPSSLTransport;
import com.sun.mail.smtp.SMTPTransport;

import javax.activation.DataHandler;
import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.util.ByteArrayDataSource;
import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * SMTP e-posta servisi.
 *
 * Eski surumlerde kodun HIC gitmemesinin sebebi: shade/relocate edilen JavaMail'in
 * META-INF/javamail.providers ve mailcap dosyalari eski (relocate edilmemis) sinif adlarini
 * gosteriyordu -> Transport bulunamiyor / "no object DCH for MIME type" hatasi.
 *
 * Bu surum:
 *  - SMTP(S)Transport'u dogrudan olusturur (provider dosyasina ihtiyac yok),
 *  - icerigi DataSource ile verir (mailcap'e ihtiyac yok),
 *  - her gonderimde acik connect(host, port, user, pass) ile kimlik dogrular,
 *  - ayri bir thread'de calisir (oyun asla donmaz),
 *  - hatalari anlasilir sekilde konsola ve yetkiliye bildirir.
 */
public final class EmailService {

    /** TLSv1.3 sadece JVM destekliyorsa eklenir (eski Java 8 surumleriyle uyum). */
    private static final String TLS_PROTOCOLS = detectProtocols();

    private final KayitPlugin plugin;
    private ExecutorService executor;
    private volatile String lastError;
    private volatile boolean lastTestOk;

    public EmailService(KayitPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "KayitPlugin-Mail");
                t.setDaemon(true);
                return t;
            }
        });
        lastError = null;
        lastTestOk = false;
        Settings s = plugin.settings();
        if (!s.emailEnabled) {
            return;
        }
        if (!s.isMailConfigured()) {
            plugin.getLogger().warning(console("console.mail-not-configured"));
            return;
        }
        plugin.getLogger().info(console("console.mail-ready", "host", s.smtpHost, "port", s.smtpPort,
                "security", s.mailSecurity.name()));
        if (s.mailTestOnStartup) {
            submit(new Supplier<MailResult>() {
                @Override
                public MailResult get() {
                    return testConnectionNow();
                }
            }).thenAccept(r -> {
                if (r.isSuccess()) {
                    plugin.getLogger().info(console("console.mail-test-ok", "ms", r.getMillis()));
                } else {
                    plugin.getLogger().warning(console("console.mail-test-failed", "error", r.getError()));
                }
            });
        }
    }

    public void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** E-posta sistemi acik VE ayarlanmis mi? */
    public boolean isReady() {
        Settings s = plugin.settings();
        return s.emailEnabled && s.isMailConfigured();
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isLastTestOk() {
        return lastTestOk;
    }

    // ---------------------------------------------------------------- public API

    /**
     * Dil dosyasindaki sablonla e-posta gonderir.
     * Hazir yer tutucular: {server} {seconds} {code_command}
     */
    public CompletableFuture<MailResult> sendTemplate(final String to, String template, Object... kv) {
        Settings s = plugin.settings();
        Object[] base = {
                "server", s.serverName,
                "seconds", s.codeExpirySeconds,
                "code_command", plugin.commands() == null ? "kod" : plugin.commands().label("code")
        };
        Object[] all = new Object[base.length + (kv == null ? 0 : kv.length)];
        System.arraycopy(base, 0, all, 0, base.length);
        if (kv != null) {
            System.arraycopy(kv, 0, all, base.length, kv.length);
        }
        final String subject = Text.strip(Text.replace(plugin.lang().raw("email-templates." + template + ".subject"), all));
        final String body = Text.replace(plugin.lang().raw("email-templates." + template + ".body"), all);
        String code = null;
        for (int i = 0; i + 1 < all.length; i += 2) {
            if ("code".equals(String.valueOf(all[i]))) {
                code = String.valueOf(all[i + 1]);
            }
        }
        final String highlight = code;
        return submit(new Supplier<MailResult>() {
            @Override
            public MailResult get() {
                return sendNow(to, subject, body, highlight);
            }
        });
    }

    // ---------------------------------------------------------------- dahili

    private CompletableFuture<MailResult> submit(Supplier<MailResult> job) {
        if (executor == null || executor.isShutdown()) {
            return CompletableFuture.completedFuture(MailResult.fail("mail service stopped", 0));
        }
        try {
            return CompletableFuture.supplyAsync(job, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(MailResult.fail("mail service stopped", 0));
        }
    }

    private MailResult testConnectionNow() {
        long start = System.currentTimeMillis();
        Settings s = plugin.settings();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(EmailService.class.getClassLoader());
        Transport transport = null;
        try {
            Session session = Session.getInstance(properties(s));
            transport = transport(session, s);
            transport.connect(s.smtpHost, s.smtpPort, s.smtpUser, s.smtpPassword);
            lastTestOk = true;
            lastError = null;
            return MailResult.ok(System.currentTimeMillis() - start);
        } catch (Throwable t) {
            String err = describe(t, s);
            lastTestOk = false;
            lastError = err;
            return MailResult.fail(err, System.currentTimeMillis() - start);
        } finally {
            closeQuietly(transport);
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private MailResult sendNow(String to, String subject, String body, String code) {
        long start = System.currentTimeMillis();
        Settings s = plugin.settings();
        if (!isReady()) {
            String err = plugin.lang().raw("mail-errors.not-configured");
            return MailResult.fail(err, 0);
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(EmailService.class.getClassLoader());
        Transport transport = null;
        try {
            Session session = Session.getInstance(properties(s));

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(s.fromAddress, s.fromName, "UTF-8"));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
            msg.setSubject(subject, "UTF-8");
            msg.setSentDate(new Date());

            String type = s.mailHtml ? "text/html; charset=UTF-8" : "text/plain; charset=UTF-8";
            String content = s.mailHtml ? html(subject, body, code, s.serverName) : body;
            ByteArrayDataSource ds = new ByteArrayDataSource(content.getBytes(StandardCharsets.UTF_8), type);
            msg.setDataHandler(new DataHandler(ds));
            msg.setHeader("Content-Type", type);
            msg.saveChanges();

            transport = transport(session, s);
            transport.connect(s.smtpHost, s.smtpPort, s.smtpUser, s.smtpPassword);
            transport.sendMessage(msg, msg.getAllRecipients());

            lastError = null;
            plugin.getLogger().info(console("console.mail-sent", "email", to));
            return MailResult.ok(System.currentTimeMillis() - start);
        } catch (Throwable t) {
            String err = describe(t, s);
            lastError = err;
            plugin.getLogger().warning(console("console.mail-failed", "email", to, "error", err));
            return MailResult.fail(err, System.currentTimeMillis() - start);
        } finally {
            closeQuietly(transport);
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static Transport transport(Session session, Settings s) {
        boolean ssl = s.mailSecurity == Settings.MailSecurity.SSL;
        URLName url = new URLName(ssl ? "smtps" : "smtp", s.smtpHost, s.smtpPort, null, s.smtpUser, s.smtpPassword);
        return ssl ? new SMTPSSLTransport(session, url) : new SMTPTransport(session, url);
    }

    private static Properties properties(Settings s) {
        Properties p = new Properties();
        String timeout = String.valueOf(s.mailTimeoutSeconds * 1000);
        boolean ssl = s.mailSecurity == Settings.MailSecurity.SSL;
        p.setProperty("mail.transport.protocol", ssl ? "smtps" : "smtp");
        p.setProperty("mail.from", s.fromAddress);
        p.setProperty("mail.mime.charset", "UTF-8");
        for (String proto : new String[]{"smtp", "smtps"}) {
            String pre = "mail." + proto + ".";
            p.setProperty(pre + "host", s.smtpHost);
            p.setProperty(pre + "port", String.valueOf(s.smtpPort));
            p.setProperty(pre + "auth", "true");
            p.setProperty(pre + "connectiontimeout", timeout);
            p.setProperty(pre + "timeout", timeout);
            p.setProperty(pre + "writetimeout", timeout);
            p.setProperty(pre + "localhost", s.heloName.isEmpty() ? "localhost" : s.heloName);
            p.setProperty(pre + "ssl.trust", s.smtpHost);
            p.setProperty(pre + "ssl.protocols", TLS_PROTOCOLS);
        }
        if (s.mailSecurity == Settings.MailSecurity.STARTTLS) {
            p.setProperty("mail.smtp.starttls.enable", "true");
            p.setProperty("mail.smtp.starttls.required", "true");
        } else if (ssl) {
            p.setProperty("mail.smtps.ssl.enable", "true");
            p.setProperty("mail.smtp.ssl.enable", "true");
        }
        return p;
    }

    private static String detectProtocols() {
        try {
            String[] supported = javax.net.ssl.SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
            for (String proto : supported) {
                if ("TLSv1.3".equals(proto)) {
                    return "TLSv1.2 TLSv1.3";
                }
            }
        } catch (Throwable ignored) {
            // varsayilan
        }
        return "TLSv1.2";
    }

    private static void closeQuietly(Transport t) {
        if (t != null) {
            try {
                t.close();
            } catch (Throwable ignored) {
                // yok say
            }
        }
    }

    /** Exception'i yetkilinin anlayacagi aciklamaya cevirir. */
    private String describe(Throwable t, Settings s) {
        Object[] kv = {"host", s.smtpHost, "port", s.smtpPort};
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof AuthenticationFailedException) {
                return Text.replace(plugin.lang().raw("mail-errors.auth"), kv);
            }
            if (cur instanceof UnknownHostException) {
                return Text.replace(plugin.lang().raw("mail-errors.unknown-host"), kv);
            }
            if (cur instanceof SocketTimeoutException) {
                return Text.replace(plugin.lang().raw("mail-errors.timeout"), kv);
            }
            if (cur instanceof ConnectException || cur.getClass().getSimpleName().equals("MailConnectException")) {
                return Text.replace(plugin.lang().raw("mail-errors.connect"), kv);
            }
            if (cur instanceof SSLException) {
                return Text.replace(plugin.lang().raw("mail-errors.tls"), kv);
            }
            cur = cur.getCause();
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        msg = msg.replace('\n', ' ').replace('\r', ' ').trim();
        if (msg.length() > 200) {
            msg = msg.substring(0, 200) + "...";
        }
        return Text.replace(plugin.lang().raw("mail-errors.other"), "error", msg);
    }

    private String console(String path, Object... kv) {
        return Text.strip(Text.replace(plugin.lang().raw(path), kv));
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String html(String subject, String body, String code, String server) {
        String text = escape(body.trim()).replace("\r\n", "\n").replace("\n", "<br>\n");
        if (code != null && !code.isEmpty()) {
            text = text.replace(code, "<span style=\"display:inline-block;font-size:24px;font-weight:bold;"
                    + "letter-spacing:4px;color:#1a73e8;background:#eef4ff;padding:4px 12px;border-radius:6px\">"
                    + code + "</span>");
        }
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>" + escape(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:24px;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif\">"
                + "<div style=\"max-width:540px;margin:0 auto;background:#ffffff;border-radius:12px;"
                + "border:1px solid #e3e6ea;overflow:hidden\">"
                + "<div style=\"background:#1a73e8;color:#ffffff;padding:18px 24px;font-size:20px;font-weight:bold\">"
                + escape(server) + "</div>"
                + "<div style=\"padding:24px;color:#202124;font-size:15px;line-height:1.6\">" + text + "</div>"
                + "</div></body></html>";
    }
}
