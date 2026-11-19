package com.jestron.kayit.storage;

import java.util.UUID;

/** Kayitli bir hesabin degismez goruntusu. */
public final class PlayerAccount {

    private final UUID uuid;
    private final String name;
    private final String passwordHash;
    private final String email;
    private final boolean emailVerified;
    private final long registeredAt;
    private final long lastLogin;
    private final long lastSeen;
    private final String lastIp;

    public PlayerAccount(UUID uuid, String name, String passwordHash, String email, boolean emailVerified,
                         long registeredAt, long lastLogin, long lastSeen, String lastIp) {
        this.uuid = uuid;
        this.name = name;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.email = email == null || email.trim().isEmpty() ? null : email.trim();
        this.emailVerified = emailVerified;
        this.registeredAt = registeredAt;
        this.lastLogin = lastLogin;
        this.lastSeen = lastSeen;
        this.lastIp = lastIp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Sifre sifirlanmis hesaplarda hash bostur = kayitsiz sayilir. */
    public boolean isRegistered() {
        return !passwordHash.isEmpty();
    }

    public String getEmail() {
        return email;
    }

    public boolean hasEmail() {
        return email != null;
    }

    public boolean isEmailVerified() {
        return emailVerified && email != null;
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

    public String getLastIp() {
        return lastIp;
    }
}
