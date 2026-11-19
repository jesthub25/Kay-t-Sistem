package com.jestron.kayit.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Veritabani erisimi. Tum metodlar thread-safe'dir ve ANA THREAD'DEN CAGRILMAMALIDIR
 * (eklenti bunlari her zaman arka plan thread'inde calistirir).
 */
public interface Database {

    void init() throws Exception;

    void close();

    String describe();

    // ---------------------------------------------------------------- hesaplar

    PlayerAccount getAccount(UUID uuid) throws Exception;

    PlayerAccount getAccountByName(String name) throws Exception;

    /** Yeni kayit veya sifresi sifirlanmis hesabi yeniden kaydeder. email null ise mevcut e-posta korunur. */
    void saveRegistration(UUID uuid, String name, String passwordHash, String email, boolean emailVerified,
                          String ip, long now) throws Exception;

    void setPassword(UUID uuid, String passwordHash) throws Exception;

    void setEmail(UUID uuid, String email, boolean verified) throws Exception;

    int countAccountsByEmail(String email, UUID exclude) throws Exception;

    // ---------------------------------------------------------------- IP / aktiflik

    void recordLogin(UUID uuid, String name, String ip, long now) throws Exception;

    void recordQuit(UUID uuid, long now) throws Exception;

    int countRegistrationsByIp(String ip, UUID exclude) throws Exception;

    List<String> getIps(UUID uuid, int limit) throws Exception;

    List<AltAccount> getAccountsByIps(Collection<String> ips, int limit) throws Exception;

    // ---------------------------------------------------------------- oturumlar

    void saveSession(UUID uuid, String ip, long expiresAt) throws Exception;

    boolean hasValidSession(UUID uuid, String ip, long now) throws Exception;

    void deleteSession(UUID uuid) throws Exception;

    // ---------------------------------------------------------------- komut ban kaydi

    void logCommandBan(CommandBanRecord record) throws Exception;

    void logCommandUnban(String name, String by, long now) throws Exception;

    List<CommandBanRecord> getActiveCommandBans(long now) throws Exception;

    List<CommandBanRecord> getCommandBanHistory(Collection<String> names) throws Exception;
}
