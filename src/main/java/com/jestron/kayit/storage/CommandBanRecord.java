package com.jestron.kayit.storage;

/** Komut izleyicisinin kaydettigi ban (okunamayan ban eklentileri icin yedek kaynak). */
public final class CommandBanRecord {

    private final String name;
    private final String reason;
    private final String issuer;
    private final long created;
    private final long expires;
    private final boolean active;
    private final String plugin;

    public CommandBanRecord(String name, String reason, String issuer, long created, long expires,
                            boolean active, String plugin) {
        this.name = name;
        this.reason = reason;
        this.issuer = issuer;
        this.created = created;
        this.expires = expires;
        this.active = active;
        this.plugin = plugin;
    }

    public String getName() {
        return name;
    }

    public String getReason() {
        return reason;
    }

    public String getIssuer() {
        return issuer;
    }

    public long getCreated() {
        return created;
    }

    /** -1 = kalici */
    public long getExpires() {
        return expires;
    }

    public boolean isActive() {
        return active;
    }

    public String getPlugin() {
        return plugin;
    }
}
