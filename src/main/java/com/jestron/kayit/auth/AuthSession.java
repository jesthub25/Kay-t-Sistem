package com.jestron.kayit.auth;

import com.jestron.kayit.storage.PlayerAccount;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cevrimici bir oyuncunun giris durumu. */
public final class AuthSession {

    private final UUID uuid;
    private final String name;
    private final String ip;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private volatile long joinTime;
    private volatile PlayerAccount account;
    private volatile boolean loaded;
    private volatile boolean loggedIn;
    private volatile int failedAttempts;
    private volatile PendingAction pending;
    private volatile long lastReminder;
    private volatile long lastLoadAttempt;
    private volatile boolean alerted;
    private volatile boolean blinded;

    public AuthSession(UUID uuid, String name, String ip, long joinTime) {
        this.uuid = uuid;
        this.name = name;
        this.ip = ip;
        this.joinTime = joinTime;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public AtomicBoolean busy() {
        return busy;
    }

    public AtomicBoolean loading() {
        return loading;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(long joinTime) {
        this.joinTime = joinTime;
    }

    public PlayerAccount getAccount() {
        return account;
    }

    public void setAccount(PlayerAccount account) {
        this.account = account;
    }

    public boolean isRegistered() {
        PlayerAccount a = account;
        return a != null && a.isRegistered();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public int addFailedAttempt() {
        return ++failedAttempts;
    }

    public void resetFailedAttempts() {
        failedAttempts = 0;
    }

    public PendingAction getPending() {
        return pending;
    }

    public void setPending(PendingAction pending) {
        this.pending = pending;
    }

    public long getLastReminder() {
        return lastReminder;
    }

    public void setLastReminder(long lastReminder) {
        this.lastReminder = lastReminder;
    }

    public long getLastLoadAttempt() {
        return lastLoadAttempt;
    }

    public void setLastLoadAttempt(long lastLoadAttempt) {
        this.lastLoadAttempt = lastLoadAttempt;
    }

    public boolean isAlerted() {
        return alerted;
    }

    public void setAlerted(boolean alerted) {
        this.alerted = alerted;
    }

    public boolean isBlinded() {
        return blinded;
    }

    public void setBlinded(boolean blinded) {
        this.blinded = blinded;
    }
}
