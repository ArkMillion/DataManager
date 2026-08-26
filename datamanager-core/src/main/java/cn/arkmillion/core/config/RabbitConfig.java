package cn.arkmillion.core.config;

public class RabbitConfig {

    private String alias;
    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
    private String virtualHost = "/";
    private int timeoutMs = 10_000;
    private boolean automaticRecovery = true;

    public RabbitConfig(String host, int port) {
        if (host != null && !host.isEmpty()) {
            this.host = host;
        }
        this.port = port;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isAutomaticRecovery() {
        return automaticRecovery;
    }

    public void setAutomaticRecovery(boolean automaticRecovery) {
        this.automaticRecovery = automaticRecovery;
    }
}
