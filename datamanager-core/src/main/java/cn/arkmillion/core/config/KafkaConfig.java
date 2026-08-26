package cn.arkmillion.core.config;

public class KafkaConfig {

    private String alias;
    private String servers = "localhost:9092";
    private String acks = "1";
    private int timeoutMs = 10_000;
    private long sendTimeoutMs = 10_000;
    private int pollIntervalMs = 200;
    private String autoOffsetReset = "latest";

    public KafkaConfig(String servers) {
        if (servers != null && !servers.isEmpty()) {
            this.servers = servers;
        }
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getServers() {
        return servers;
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    public String getAcks() {
        return acks;
    }

    public void setAcks(String acks) {
        this.acks = acks;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        if (autoOffsetReset != null
                && (autoOffsetReset.equals("earliest")
                || autoOffsetReset.equals("latest")
                || autoOffsetReset.equals("none"))) {
            this.autoOffsetReset = autoOffsetReset;
        }
    }
}
