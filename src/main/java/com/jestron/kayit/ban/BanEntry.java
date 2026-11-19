package com.jestron.kayit.ban;

import com.jestron.kayit.util.Checks;

/** Herhangi bir kaynaktan okunan tek bir ban kaydi. */
public final class BanEntry {

    private final String name;
    private final String uuidKey;
    private final String reason;
    private final String issuer;
    private final long created;
    private final long expires;
    private final boolean active;
    private final String source;
    private final int priority;

    /**
     * @param uuid    UUID (tireli/tiresiz) veya null
     * @param expires bitis (ms), kalici ise -1
     * @param priority dogrudan okunan kaynaklar 10, komut izleyici 0
     */
    public BanEntry(String name, String uuid, String reason, String issuer, long created, long expires,
                    boolean active, String source, int priority) {
        this.name = name == null || name.trim().isEmpty() ? null : name.trim();
        this.uuidKey = Checks.uuidKey(uuid);
        this.reason = reason == null || reason.trim().isEmpty() ? null : reason.trim();
        this.issuer = issuer == null || issuer.trim().isEmpty() ? null : issuer.trim();
        this.created = created;
        this.expires = expires <= 0 ? -1L : expires;
        this.active = active;
        this.source = source;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public String getUuidKey() {
        return uuidKey;
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

    public long getExpires() {
        return expires;
    }

    public boolean isPermanent() {
        return expires < 0;
    }

    public String getSource() {
        return source;
    }

    public int getPriority() {
        return priority;
    }

    /** Kayit aktif mi VE suresi dolmamis mi? */
    public boolean isActiveNow() {
        return active && (expires < 0 || expires > System.currentTimeMillis());
    }

    /** a, b'den daha "iyi" bir kayit mi? (once oncelik, sonra daha yeni tarih) */
    public static boolean better(BanEntry a, BanEntry b) {
        if (b == null) {
            return true;
        }
        if (a.priority != b.priority) {
            return a.priority > b.priority;
        }
        return a.created > b.created;
    }
}
