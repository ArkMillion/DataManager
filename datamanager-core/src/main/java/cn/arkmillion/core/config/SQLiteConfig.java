package cn.arkmillion.core.config;

public class SQLiteConfig extends DataSourceConfig {

    private String filePath;
    private int busyTimeoutMs = 5_000;
    private boolean foreignKeys = true;

    public SQLiteConfig(String filePath) {
        this.filePath = filePath;
        setPoolSize(1);
        setMinIdle(1);
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getBusyTimeoutMs() {
        return busyTimeoutMs;
    }

    public void setBusyTimeoutMs(int busyTimeoutMs) {
        this.busyTimeoutMs = busyTimeoutMs;
    }

    public boolean isForeignKeys() {
        return foreignKeys;
    }

    public void setForeignKeys(boolean foreignKeys) {
        this.foreignKeys = foreignKeys;
    }
}
