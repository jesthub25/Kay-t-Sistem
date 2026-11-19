package com.jestron.kayit.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * SQLite. Surucu (org.sqlite.JDBC) sunucunun kendisinden kullanilir; JAR'a gomulmez.
 */
public final class SQLiteDatabase extends SqlDatabase {

    private final File file;

    public SQLiteDatabase(File file, Logger logger) {
        super(logger);
        this.file = file;
    }

    @Override
    protected Connection open() throws Exception {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create folder " + parent);
        }
        Driver driver;
        try {
            driver = (Driver) Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite driver (org.sqlite.JDBC) not found on this server. "
                    + "Use database.type: mysql in config.yml.", e);
        }
        Connection c = driver.connect("jdbc:sqlite:" + file.getAbsolutePath(), new Properties());
        if (c == null) {
            throw new IllegalStateException("SQLite driver refused the connection URL");
        }
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
        } catch (Exception e) {
            logger.warning("SQLite PRAGMA could not be applied: " + e.getMessage());
        }
        return c;
    }

    @Override
    protected boolean isMySql() {
        return false;
    }

    @Override
    public String describe() {
        return "SQLite (" + file.getName() + ")";
    }
}
