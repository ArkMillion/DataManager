package cn.arkmillion.postgresql;

import cn.arkmillion.core.jdbc.JdbcDialect;

public enum PgDialect implements JdbcDialect {

    INSTANCE;

    @Override
    public String dialectName() {
        return "postgres";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
