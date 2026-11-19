package com.jestron.kayit.mail;

/** E-posta gonderim sonucu. */
public final class MailResult {

    private final boolean success;
    private final String error;
    private final long millis;

    private MailResult(boolean success, String error, long millis) {
        this.success = success;
        this.error = error;
        this.millis = millis;
    }

    public static MailResult ok(long millis) {
        return new MailResult(true, null, millis);
    }

    public static MailResult fail(String error, long millis) {
        return new MailResult(false, error, millis);
    }

    public boolean isSuccess() {
        return success;
    }

    /** Kullaniciya/yetkiliye gosterilebilir hata aciklamasi. */
    public String getError() {
        return error;
    }

    public long getMillis() {
        return millis;
    }
}
