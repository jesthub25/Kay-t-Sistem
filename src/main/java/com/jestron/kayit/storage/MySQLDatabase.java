package com.jestron.kayit.storage;

import java.sql.Connection;
import java.sql.Driver;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * MySQL / MariaDB. Sunucunun icindeki surucu kullanilir (yeni: com.mysql.cj.jdbc.Driver,
 * 1.16: com.mysql.jdbc.Driver, varsa org.mariadb.jdbc.Driver).
 */
public final class MySQLDatabase extends SqlDatabase {

    private final boolean preferMaria;
    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final boolean ssl;

    public MySQLDatabase(boolean preferMaria, String host, int port, String database, String user, String password,
                         boolean ssl, Logger logger) {
        super(logger);
        this.preferMaria = preferMaria;
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
        this.ssl = ssl;
    }

    @Override
    protected Connection open() throws Exception {
        return connect(preferMaria, host, port, database, user, password, ssl);
    }

    /** Disaridan (ban kaynaklari) da kullanilabilen baglanti yardimcisi. */
    public static Connection connect(boolean preferMaria, String host, int port, String database, String user,
                                     String password, boolean ssl) throws Exception {
        String[][] candidates = preferMaria
                ? new String[][]{{"org.mariadb.jdbc.Driver", "mariadb"}, {"com.mysql.cj.jdbc.Driver", "mysql"},
                {"com.mysql.jdbc.Driver", "mysql"}}
                : new String[][]{{"com.mysql.cj.jdbc.Driver", "mysql"}, {"com.mysql.jdbc.Driver", "mysql"},
                {"org.mariadb.jdbc.Driver", "mariadb"}};
        Exception last = null;
        for (String[] c : candidates) {
            Driver driver;
            try {
                driver = (Driver) Class.forName(c[0]).getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                continue;
            } catch (Throwable t) {
                last = new Exception(t);
                continue;
            }
            Properties p = new Properties();
            p.setProperty("user", user == null ? "" : user);
            p.setProperty("password", password == null ? "" : password);
            p.setProperty("useSSL", String.valueOf(ssl));
            p.setProperty("characterEncoding", "utf8");
            p.setProperty("useUnicode", "true");
            p.setProperty("serverTimezone", "UTC");
            p.setProperty("connectTimeout", "10000");
            p.setProperty("socketTimeout", "30000");
            p.setProperty("tcpKeepAlive", "true");
            if (c[0].startsWith("com.mysql.cj") && !ssl) {
                p.setProperty("allowPublicKeyRetrieval", "true");
            }
            String url = "jdbc:" + c[1] + "://" + host + ":" + port + "/" + database;
            try {
                Connection conn = driver.connect(url, p);
                if (conn != null) {
                    return conn;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("No MySQL/MariaDB JDBC driver found on this server.");
    }

    @Override
    protected boolean isMySql() {
        return true;
    }

    @Override
    public String describe() {
        return (preferMaria ? "MariaDB" : "MySQL") + " (" + host + ":" + port + "/" + database + ")";
    }
}
