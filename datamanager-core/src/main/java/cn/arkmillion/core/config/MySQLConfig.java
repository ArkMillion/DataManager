package cn.arkmillion.core.config;

public class MySQLConfig extends DataSourceConfig {

    private String charset = "utf8mb4";
    private boolean sslEnabled = false;

    public MySQLConfig(String url, String username, String password) {
        setUrl(url);
        setUsername(username);
        setPassword(password);
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }
}
