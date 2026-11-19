package com.jestron.kayit.ban;

import com.jestron.kayit.storage.AltAccount;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Bir ban eklentisinden/kaynagindan veri okuyucu. */
public abstract class BanProvider {

    private volatile String lastError;
    private volatile boolean announced;

    /** Gorunen ad (LiteBans, AdvancedBan ...). */
    public abstract String name();

    /** Bu kaynak su an kullanilabilir mi (eklenti yuklu, ayar acik)? */
    public abstract boolean isAvailable();

    /** Bu kaynagin ana thread'de okunmasi gerekiyor mu? */
    public boolean mainThread() {
        return false;
    }

    /** Su an aktif olan banlar. */
    public abstract List<BanEntry> fetchActive() throws Exception;

    /** Verilen hesaplarin gecmis banlari (aktif olmayanlar dahil). */
    public List<BanEntry> fetchHistory(Collection<AltAccount> accounts) throws Exception {
        return Collections.emptyList();
    }

    /** Bu kaynak, verilen eklentinin banlarini dogrudan okuyabiliyor mu? */
    public boolean covers(String pluginName) {
        return false;
    }

    public void shutdown() {
    }

    public String getLastError() {
        return lastError;
    }

    void setLastError(String error) {
        this.lastError = error;
    }

    boolean markAnnounced() {
        if (announced) {
            return false;
        }
        announced = true;
        return true;
    }

    void resetAnnounced() {
        announced = false;
    }
}
