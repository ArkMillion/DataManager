package cn.arkmillion.core.jdbc;

public interface JdbcDialect {

    String dialectName();

    String quoteIdentifier(String identifier);

    default String limitClause(String baseSql, int limit, int offset) {
        return baseSql + " LIMIT " + limit + " OFFSET " + offset;
    }

    JdbcDialect DEFAULT_DIALECT = new JdbcDialect() {

        @Override
        public String dialectName() {
            return "generic";
        }

        @Override
        public String quoteIdentifier(String identifier) {
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }
    };
}
