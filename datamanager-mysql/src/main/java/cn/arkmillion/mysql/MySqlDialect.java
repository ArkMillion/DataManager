package cn.arkmillion.mysql;

import cn.arkmillion.core.jdbc.JdbcDialect;

public enum MySqlDialect implements JdbcDialect {

    INSTANCE;

    @Override
    public String dialectName() {
        return "mysql";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
