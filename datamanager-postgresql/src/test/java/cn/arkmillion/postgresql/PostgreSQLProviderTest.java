package cn.arkmillion.postgresql;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.DataType;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.metadata.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSQLProviderTest {

    @Test
    void dialectNameAndQuoting() {
        assertEquals("postgres", PgDialect.INSTANCE.dialectName());
        assertEquals("\"user\"", PgDialect.INSTANCE.quoteIdentifier("user"));
        assertEquals("\"we\"\"ird\"", PgDialect.INSTANCE.quoteIdentifier("we\"ird"));
        String limited = PgDialect.INSTANCE.limitClause("SELECT * FROM t", 20, 40);
        assertEquals("SELECT * FROM t LIMIT 20 OFFSET 40", limited);
    }

    @Test
    void providerCreatesMultipleAliasBindings() {
        PostgreSQLProvider provider = new PostgreSQLProvider();
        assertEquals("postgres", provider.name());

        DataManagerConfig config = DataManagerConfig.builder()
                .postgres("jdbc:postgresql://localhost:5432/main", "u", "p")
                .alias("core-db")
                .minIdle(0)
                .poolSize(1)
                .initializationFailTimeout(-1)
                .build()
                .postgres("jdbc:postgresql://localhost:5432/analytics", "u", "p")
                .minIdle(0)
                .poolSize(1)
                .initializationFailTimeout(-1)
                .build()
                .build();

        List<InstanceBinding<RelationalDB>> bindings = provider.createInstances(config);
        try {
            assertEquals(2, bindings.size());
            assertEquals("core-db", bindings.get(0).getName());
            assertEquals("postgres", bindings.get(1).getName());
            assertTrue(bindings.get(0).getInstance() instanceof PostgreSQLAdapter);
        } finally {
            for (InstanceBinding<RelationalDB> binding : bindings) {
                binding.getInstance().close();
            }
        }
    }

    @Test
    void emptyConfigProducesNoBindings() {
        PostgreSQLProvider provider = new PostgreSQLProvider();
        assertTrue(provider.createInstances(
                DataManagerConfig.builder().sqlite("x.db").build().build()).isEmpty());
    }

    @Test
    void typeMappingCoversAllDataTypes() throws Exception {
        java.lang.reflect.Method sqlType = PostgreSQLSchemaSynchronizer.class
                .getDeclaredMethod("sqlType", ColumnMetadata.class);
        sqlType.setAccessible(true);
        Object synchronizer = new PostgreSQLSchemaSynchronizer(null);

        assertEquals("SMALLINT", invoke(sqlType, synchronizer, DataType.TINYINT));
        assertEquals("INTEGER", invoke(sqlType, synchronizer, DataType.INT));
        assertEquals("BIGINT", invoke(sqlType, synchronizer, DataType.BIGINT));
        assertEquals("REAL", invoke(sqlType, synchronizer, DataType.FLOAT));
        assertEquals("DOUBLE PRECISION", invoke(sqlType, synchronizer, DataType.DOUBLE));
        assertEquals("BOOLEAN", invoke(sqlType, synchronizer, DataType.BOOLEAN));
        assertEquals("TEXT", invoke(sqlType, synchronizer, DataType.TEXT));
        assertEquals("BYTEA", invoke(sqlType, synchronizer, DataType.BLOB));
        assertEquals("TIMESTAMP", invoke(sqlType, synchronizer, DataType.DATETIME));
        assertEquals("VARCHAR(64)", invoke(sqlType, synchronizer, DataType.VARCHAR, 64));
    }

    private static String invoke(java.lang.reflect.Method method, Object target, DataType type) throws Exception {
        return invoke(method, target, type, 255);
    }

    private static String invoke(java.lang.reflect.Method method, Object target, DataType type, int length) throws Exception {
        ColumnMetadata cm = new ColumnMetadata();
        cm.setDataType(type);
        cm.setLength(length);
        return (String) method.invoke(target, cm);
    }
}
