package cn.arkmillion.core.config;

public class PostgresConfig extends DataSourceConfig {

    private String schema = "public";

    public PostgresConfig(String url, String username, String password) {
        setUrl(url);
        setUsername(username);
        setPassword(password);
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
