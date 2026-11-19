package com.jestron.kayit.auth;

/** E-posta kodu bekleyen islem. */
public final class PendingAction {

    public enum Type {
        REGISTER("register"),
        LOGIN("login"),
        EMAIL_ADD("email-add"),
        EMAIL_REMOVE("email-remove"),
        EMAIL_CHANGE("email-change");

        private final String template;

        Type(String template) {
            this.template = template;
        }

        public String template() {
            return template;
        }
    }

    private final Type type;
    private final String passwordHash;
    private final String targetEmail;
    private final String oldEmail;
    private volatile String code;
    private volatile long expiresAt;
    private volatile long lastSent;
    private volatile int attempts;

    public PendingAction(Type type, String code, long expiresAt, String passwordHash, String targetEmail,
                         String oldEmail) {
        this.type = type;
        this.code = code;
        this.expiresAt = expiresAt;
        this.passwordHash = passwordHash;
        this.targetEmail = targetEmail;
        this.oldEmail = oldEmail;
    }

    public Type getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public long getLastSent() {
        return lastSent;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getTargetEmail() {
        return targetEmail;
    }

    public String getOldEmail() {
        return oldEmail;
    }

    public boolean isExpired(long now) {
        return now > expiresAt;
    }

    public int addAttempt() {
        return ++attempts;
    }

    public void markSent(long now) {
        this.lastSent = now;
    }

    public void renew(String newCode, long newExpiresAt) {
        this.code = newCode;
        this.expiresAt = newExpiresAt;
        this.attempts = 0;
    }
}
