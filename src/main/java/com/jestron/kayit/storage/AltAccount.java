package com.jestron.kayit.storage;

import java.util.UUID;

/** Ortak IP uzerinden bulunan hesap. */
public final class AltAccount {

    private final UUID uuid;
    private final String name;
    private final boolean registered;
    private final long registeredAt;
    private final long lastLogin;
    private final long lastSeen;
    private final int sharedIps;

    public AltAccount(UUID uuid, String name, boolean registered, long registeredAt, long lastLogin,
                      long lastSeen, int sharedIps) {
        this.uuid = uuid;
        this.name = name;
        this.registered = registered;
        this.registeredAt = registeredAt;
        this.lastLogin = lastLogin;
        this.lastSeen = lastSeen;
        this.sharedIps = sharedIps;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public boolean isRegistered() {
        return registered;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    /** Son aktiflik: cikis zamani, yoksa son giris zamani. */
    public long getLastActive() {
        return Math.max(lastSeen, lastLogin);
    }

    public int getSharedIps() {
        return sharedIps;
    }
}
