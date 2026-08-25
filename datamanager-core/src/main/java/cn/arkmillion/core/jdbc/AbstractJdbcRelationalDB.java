package cn.arkmillion.core.jdbc;

import cn.arkmillion.core.condition.Condition;
import cn.arkmillion.core.condition.Criterion;
import cn.arkmillion.core.condition.Order;
import cn.arkmillion.core.condition.PageParam;
import cn.arkmillion.core.condition.PageResult;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.Operator;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.metadata.ColumnMetadata;
import cn.arkmillion.core.metadata.EntityParser;
import cn.arkmillion.core.metadata.SchemaDefinition;
import cn.arkmillion.core.metrics.MetricsCollector;
import cn.arkmillion.core.monitor.ConnectionMonitor;
import cn.arkmillion.core.schema.SchemaSynchronizer;
import cn.arkmillion.core.util.SqlScripts;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractJdbcRelationalDB implements RelationalDB {

    protected final DataSource dataSource;
    protected final JdbcDialect dialect;
    private SchemaSynchronizer synchronizer;
    private final String poolName;
    private final ThreadLocal<Connection> txConnection = new ThreadLocal<>();
    private final Map<Class<?>, SchemaDefinition> schemaCache = new ConcurrentHashMap<>();

    protected AbstractJdbcRelationalDB(String poolName, DataSource dataSource, JdbcDialect dialect) {
        this.poolName = poolName;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    protected abstract SchemaSynchronizer createSynchronizer();

    protected final SchemaSynchronizer synchronizer() {
        if (synchronizer == null) {
            synchronizer = createSynchronizer();
        }
        return synchronizer;
    }

    protected Connection connection() {
        Connection tx = txConnection.get();
        if (tx != null) {
            return tx;
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            ConnectionMonitor.getInstance().notifyError(poolName, e);
            throw new DataManagerException("Failed to obtain connection from pool '" + poolName + "'", e);
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private void release(Connection conn) {
        if (txConnection.get() == null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOG.debug("Failed to close connection", e);
            }
        }
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractJdbcRelationalDB.class);

    protected SchemaDefinition definition(Class<?> entityClass) {
        return schemaCache.computeIfAbsent(entityClass, EntityParser::parse);
    }

    @Override
    public <T> int insert(T entity) {
        long start = System.currentTimeMillis();
        Class<?> clazz = entity.getClass();
        SchemaDefinition def = definition(clazz);
        List<ColumnMetadata> columns = def.getInsertableColumns();

        ColumnMetadata idColumn = def.getIdColumn();
        if (idColumn != null && idColumn.getGenerationType() == GenerationType.UUID && idColumn.getValue(entity) == null) {
            idColumn.setValue(entity, UUID.randomUUID().toString());
            if (!containsColumn(columns, idColumn)) {
                columns = new ArrayList<>(columns);
                columns.add(idColumn);
            }
        }

        StringBuilder sb = new StringBuilder("INSERT INTO ").append(dialect.quoteIdentifier(def.getTableName())).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(dialect.quoteIdentifier(columns.get(i).getColumnName()));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        sb.append(')');

        boolean returnKeys = idColumn != null && (idColumn.isAutoIncrement() || idColumn.getGenerationType() == GenerationType.IDENTITY);
        String sql = sb.toString();
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(sql,
                returnKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            bindEntityParams(ps, columns, entity);
            int affected = ps.executeUpdate();
            if (returnKeys && affected > 0) {
                applyGeneratedKey(ps, entity, idColumn);
            }
            MetricsCollector.getInstance().incrementCounter("insert", dialect.dialectName());
            return affected;
        } catch (SQLException e) {
            throw new DataManagerException("Insert failed on table '" + def.getTableName() + "': " + e.getMessage(), e);
        } finally {
            release(conn);
            recordElapsed(start, sql);
        }
    }

    @Override
    public <T> int batchInsert(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        long start = System.currentTimeMillis();
        Class<?> clazz = entities.get(0).getClass();
        SchemaDefinition def = definition(clazz);
        List<ColumnMetadata> columns = def.getInsertableColumns();

        ColumnMetadata idColumn = def.getIdColumn();
        boolean generateUuids = idColumn != null && idColumn.getGenerationType() == GenerationType.UUID;
        List<ColumnMetadata> effectiveColumns = columns;
        if (generateUuids && !containsColumn(columns, idColumn)) {
            effectiveColumns = new ArrayList<>(columns);
            effectiveColumns.add(idColumn);
        }

        StringBuilder sb = new StringBuilder("INSERT INTO ").append(dialect.quoteIdentifier(def.getTableName())).append(" (");
        for (int i = 0; i < effectiveColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(dialect.quoteIdentifier(effectiveColumns.get(i).getColumnName()));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < effectiveColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        sb.append(')');

        int total = 0;
        String sql = sb.toString();
        boolean ownTx = !isInTransaction();
        try {
            if (ownTx) {
                beginTransaction();
            }
            Connection txConn = txConnection.get() != null ? txConnection.get() : dataSource.getConnection();
            try (PreparedStatement ps = txConn.prepareStatement(sql)) {
                for (T entity : entities) {
                    if (generateUuids && idColumn.getValue(entity) == null) {
                        idColumn.setValue(entity, UUID.randomUUID().toString());
                    }
                    bindEntityParams(ps, effectiveColumns, entity);
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                for (int r : results) {
                    total += Math.max(r, 0);
                }
            }
            if (ownTx) {
                commit();
            }
            MetricsCollector.getInstance().incrementCounter("batchInsert", dialect.dialectName());
            return total;
        } catch (Exception e) {
            if (ownTx && isInTransaction()) {
                try {
                    rollback();
                } catch (RuntimeException re) {
                    e.addSuppressed(re);
                }
            }
            if (e instanceof DataManagerException) {
                throw (DataManagerException) e;
            }
            throw new DataManagerException("Batch insert failed on table '" + def.getTableName() + "'", e);
        } finally {
            recordElapsed(start, sql);
        }
    }

    private boolean containsColumn(List<ColumnMetadata> columns, ColumnMetadata target) {
        for (ColumnMetadata c : columns) {
            if (c.getPropertyName().equals(target.getPropertyName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void applyGeneratedKey(PreparedStatement ps, Object entity, ColumnMetadata idColumn) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                Object raw = keys.getObject(1);
                Object converted = TypeBinders.convert(raw, idColumn.getJavaType());
                idColumn.setValue(entity, converted);
            }
        }
    }

    @Override
    public <T> int update(T entity) {
        long start = System.currentTimeMillis();
        SchemaDefinition def = definition(entity.getClass());
        ColumnMetadata idColumn = def.getIdColumn();
        if (idColumn == null || idColumn.getValue(entity) == null) {
            throw new DataManagerException("update(entity) requires a non-null primary key: " + def.getTableName());
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder("UPDATE ").append(dialect.quoteIdentifier(def.getTableName())).append(" SET ");
        boolean first = true;
        for (ColumnMetadata c : def.getColumns()) {
            if (c.isPrimaryId()) {
                continue;
            }
            Object value = c.getValue(entity);
            if (value == null) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(dialect.quoteIdentifier(c.getColumnName())).append(" = ?");
            params.add(value);
            first = false;
        }
        if (first) {
            return 0;
        }
        sb.append(" WHERE ").append(dialect.quoteIdentifier(idColumn.getColumnName())).append(" = ?");
        params.add(idColumn.getValue(entity));

        String sql = sb.toString();
        int affected = executeUpdateSql(sql, params);
        MetricsCollector.getInstance().incrementCounter("update", dialect.dialectName());
        recordElapsed(start, sql);
        return affected;
    }

    @Override
    public <T> int update(Class<T> clazz, Condition condition, Map<String, Object> updates) {
        long start = System.currentTimeMillis();
        if (condition == null || condition.isEmpty()) {
            throw new DataManagerException("Refusing UPDATE without a condition");
        }
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        SchemaDefinition def = definition(clazz);
        StringBuilder sb = new StringBuilder("UPDATE ").append(dialect.quoteIdentifier(def.getTableName())).append(" SET ");
        List<Object> params = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(resolveColumn(def, e.getKey())).append(" = ?");
            params.add(e.getValue());
            first = false;
        }
        appendWhere(def, sb, condition, params);
        int affected = executeUpdateSql(sb.toString(), params);
        MetricsCollector.getInstance().incrementCounter("update", dialect.dialectName());
        recordElapsed(start, sb.toString());
        return affected;
    }

    @Override
    public <T> int delete(Class<T> clazz, Condition condition) {
        long start = System.currentTimeMillis();
        if (condition == null || condition.isEmpty()) {
            throw new DataManagerException("Refusing DELETE without a condition");
        }
        SchemaDefinition def = definition(clazz);
        StringBuilder sb = new StringBuilder("DELETE FROM ").append(dialect.quoteIdentifier(def.getTableName()));
        List<Object> params = new ArrayList<>();
        appendWhere(def, sb, condition, params);
        int affected = executeUpdateSql(sb.toString(), params);
        MetricsCollector.getInstance().incrementCounter("delete", dialect.dialectName());
        recordElapsed(start, sb.toString());
        return affected;
    }

    @Override
    public <T> int deleteById(Class<T> clazz, Object id) {
        long start = System.currentTimeMillis();
        SchemaDefinition def = definition(clazz);
        ColumnMetadata idColumn = def.getIdColumn();
        if (idColumn == null) {
            throw new DataManagerException("Entity has no primary key: " + clazz.getName());
        }
        String sql = "DELETE FROM " + dialect.quoteIdentifier(def.getTableName())
                + " WHERE " + dialect.quoteIdentifier(idColumn.getColumnName()) + " = ?";
        int affected = executeUpdateSql(sql, singletonList(id));
        MetricsCollector.getInstance().incrementCounter("delete", dialect.dialectName());
        recordElapsed(start, sql);
        return affected;
    }

    @Override
    public <T> List<T> select(Class<T> clazz, Condition condition) {
        long start = System.currentTimeMillis();
        SchemaDefinition def = definition(clazz);
        List<Object> params = new ArrayList<>();
        String sql = buildSelect(def, condition, params, -1, -1);
        List<T> result = queryInternal(sql, clazz, params.toArray());
        recordElapsed(start, sql);
        return result;
    }

    @Override
    public <T> T selectOne(Class<T> clazz, Condition condition) {
        long start = System.currentTimeMillis();
        SchemaDefinition def = definition(clazz);
        List<Object> params = new ArrayList<>();
        String sql = buildSelect(def, condition, params, 1, 0);
        List<T> rows = queryInternal(sql, clazz, params.toArray());
        recordElapsed(start, sql);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public <T> T selectById(Class<T> clazz, Object id) {
        SchemaDefinition def = definition(clazz);
        ColumnMetadata idColumn = def.getIdColumn();
        if (idColumn == null) {
            throw new DataManagerException("Entity has no primary key: " + clazz.getName());
        }
        Condition cond = Condition.where(idColumn.getPropertyName()).eq(id).build();
        return selectOne(clazz, cond);
    }

    @Override
    public <T> long count(Class<T> clazz, Condition condition) {
        long start = System.currentTimeMillis();
        SchemaDefinition def = definition(clazz);
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM ").append(dialect.quoteIdentifier(def.getTableName()));
        List<Object> params = new ArrayList<>();
        appendWhere(def, sb, condition == null ? Condition.empty() : condition, params);
        String sql = sb.toString();
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataManagerException("Count failed on table '" + def.getTableName() + "'", e);
        } finally {
            release(conn);
            recordElapsed(start, sql);
        }
    }

    @Override
    public <T> PageResult<T> selectPage(Class<T> clazz, Condition condition, PageParam page) {
        long start = System.currentTimeMillis();
        SchemaDefinition def = definition(clazz);
        long total = count(clazz, condition);
        if (total == 0) {
            return PageResult.empty(page);
        }
        List<Object> params = new ArrayList<>();
        String sql = buildSelect(def, condition, params, page.getPageSize(), page.getOffset());
        List<T> records = queryInternal(sql, clazz, params.toArray());
        recordElapsed(start, sql);
        return PageResult.of(records, total, page);
    }

    @Override
    public int execute(String sql, Object... params) {
        long start = System.currentTimeMillis();
        int affected = executeUpdateSql(sql, toList(params));
        MetricsCollector.getInstance().incrementCounter("execute", dialect.dialectName());
        recordElapsed(start, sql);
        return affected;
    }

    @Override
    public <T> List<T> query(String sql, Class<T> clazz, Object... params) {
        long start = System.currentTimeMillis();
        List<T> result = queryInternal(sql, clazz, params);
        recordElapsed(start, sql);
        return result;
    }

    @Override
    public List<Map<String, Object>> queryMap(String sql, Object... params) {
        long start = System.currentTimeMillis();
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, toList(params));
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DataManagerException("queryMap failed: " + e.getMessage(), e);
        } finally {
            release(conn);
            recordElapsed(start, sql);
        }
    }

    @Override
    public void beginTransaction() {
        if (txConnection.get() != null) {
            throw new DataManagerException("Transaction already active on this thread");
        }
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            txConnection.set(conn);
        } catch (SQLException e) {
            throw new DataManagerException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commit() {
        Connection conn = requireTxConnection();
        try {
            conn.commit();
        } catch (SQLException e) {
            throw new DataManagerException("Commit failed", e);
        } finally {
            finishTransaction(conn);
        }
    }

    @Override
    public void rollback() {
        Connection conn = requireTxConnection();
        try {
            conn.rollback();
        } catch (SQLException e) {
            throw new DataManagerException("Rollback failed", e);
        } finally {
            finishTransaction(conn);
        }
    }

    @Override
    public boolean isInTransaction() {
        return txConnection.get() != null;
    }

    @Override
    public void syncSchema(Class<?> entityClass, SyncMode mode) {
        synchronizer().sync(entityClass, mode);
    }

    @Override
    public void executeSqlFile(String filePath) {
        executeSqlFile(filePath, null);
    }

    @Override
    public void executeSqlFile(String filePath, Map<String, String> placeholders) {
        String content = SqlScripts.loadContent(filePath);
        content = SqlScripts.applyPlaceholders(content, placeholders);
        List<String> statements = SqlScripts.splitStatements(content);
        for (String statement : statements) {
            execute(statement);
        }
    }

    @Override
    public void close() {
        if (isInTransaction()) {
            try {
                rollback();
            } catch (RuntimeException e) {
                LOG.debug("Rollback during close failed", e);
            }
        }
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            ConnectionPool.shutdown(dataSource);
        }
    }

    private void finishTransaction(Connection conn) {
        txConnection.remove();
        try {
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            LOG.debug("Failed to reset autocommit", e);
        }
        try {
            conn.close();
        } catch (SQLException e) {
            LOG.debug("Failed to close transaction connection", e);
        }
    }

    private Connection requireTxConnection() {
        Connection conn = txConnection.get();
        if (conn == null) {
            throw new DataManagerException("No active transaction on this thread. Call beginTransaction() first.");
        }
        return conn;
    }

    private int executeUpdateSql(String sql, List<Object> params) {
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataManagerException("SQL execution failed [" + sql + "]: " + e.getMessage(), e);
        } finally {
            release(conn);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> queryInternal(String sql, Class<T> clazz, Object[] params) {
        Connection conn = connection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, toList(params));
            try (ResultSet rs = ps.executeQuery()) {
                if (isSimpleType(clazz)) {
                    List<T> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(TypeBinders.mapScalar(rs, clazz));
                    }
                    return results;
                }
                SchemaDefinition def = definition(clazz);
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    T entity = clazz.getDeclaredConstructor().newInstance();
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        ColumnMetadata cm = resolveMetadata(def, label);
                        if (cm == null) {
                            continue;
                        }
                        Object raw = rs.getObject(i);
                        Object converted = TypeBinders.convert(raw, cm.getJavaType());
                        cm.setValue(entity, converted);
                    }
                    results.add(entity);
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DataManagerException("Query failed [" + sql + "]: " + e.getMessage(), e);
        } catch (ReflectiveOperationException e) {
            throw new DataManagerException("Failed to instantiate entity " + clazz.getName()
                    + ". Ensure a public no-arg constructor exists.", e);
        } finally {
            release(conn);
        }
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class
                || clazz == byte[].class
                || java.time.temporal.Temporal.class.isAssignableFrom(clazz)
                || java.util.Date.class.isAssignableFrom(clazz);
    }

    private ColumnMetadata resolveMetadata(SchemaDefinition def, String columnLabel) {
        ColumnMetadata byName = def.findColumnByName(columnLabel);
        if (byName != null) {
            return byName;
        }
        return def.findColumnByProperty(snakeToCamel(columnLabel));
    }

    private static String snakeToCamel(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String buildSelect(SchemaDefinition def, Condition condition, List<Object> params, int limit, int offset) {
        StringBuilder sb = new StringBuilder("SELECT ");
        List<ColumnMetadata> cols = def.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(dialect.quoteIdentifier(cols.get(i).getColumnName()));
        }
        sb.append(" FROM ").append(dialect.quoteIdentifier(def.getTableName()));
        appendWhere(def, sb, condition == null ? Condition.empty() : condition, params);
        appendOrderBy(def, sb, condition);
        if (limit > 0) {
            return dialect.limitClause(sb.toString(), limit, Math.max(offset, 0));
        }
        return sb.toString();
    }

    private void appendWhere(SchemaDefinition def, StringBuilder sb, Condition condition, List<Object> params) {
        if (condition.isEmpty()) {
            return;
        }
        sb.append(" WHERE ");
        boolean first = true;
        for (Criterion criterion : condition.getCriteria()) {
            if (!first) {
                sb.append(' ').append(criterion.getConnector().name()).append(' ');
            }
            appendCriterion(def, sb, criterion, params);
            first = false;
        }
    }

    private void appendCriterion(SchemaDefinition def, StringBuilder sb, Criterion criterion, List<Object> params) {
        if (criterion.getKind() == Criterion.Kind.GROUP) {
            sb.append('(');
            boolean first = true;
            for (Criterion child : criterion.getChildren()) {
                if (!first) {
                    sb.append(' ').append(child.getConnector().name()).append(' ');
                }
                appendCriterion(def, sb, child, params);
                first = false;
            }
            sb.append(')');
            return;
        }
        String column = resolveColumn(def, criterion.getField());
        Operator op = criterion.getOperator();
        switch (op) {
            case EQ:
            case NE:
            case GT:
            case GTE:
            case LT:
            case LTE:
            case LIKE:
                sb.append(column).append(' ').append(op.symbol()).append(" ?");
                params.add(criterion.getValue());
                break;
            case IN:
            case NOT_IN:
                sb.append(column).append(' ').append(op.symbol());
                Collection<?> values = (Collection<?>) criterion.getValue();
                if (values == null || values.isEmpty()) {
                    sb.append(" (NULL)");
                    return;
                }
                sb.append(" (");
                boolean firstVal = true;
                for (Object v : values) {
                    if (!firstVal) {
                        sb.append(", ");
                    }
                    sb.append('?');
                    params.add(v);
                    firstVal = false;
                }
                sb.append(')');
                break;
            case BETWEEN:
                sb.append(column).append(" BETWEEN ? AND ?");
                Object[] range = (Object[]) criterion.getValue();
                params.add(range[0]);
                params.add(range[1]);
                break;
            case IS_NULL:
            case IS_NOT_NULL:
                sb.append(column).append(' ').append(op.symbol());
                break;
            default:
                throw new DataManagerException("Unsupported operator: " + op);
        }
    }

    private String resolveColumn(SchemaDefinition def, String field) {
        ColumnMetadata cm = def.findColumnByProperty(field);
        if (cm == null) {
            cm = def.findColumnByName(field);
        }
        return dialect.quoteIdentifier(cm != null ? cm.getColumnName() : field);
    }

    private void appendOrderBy(SchemaDefinition def, StringBuilder sb, Condition condition) {
        List<Order> orders = condition == null ? java.util.Collections.emptyList() : condition.getOrders();
        if (orders.isEmpty()) {
            ColumnMetadata idColumn = def.getIdColumn();
            if (idColumn != null) {
                sb.append(" ORDER BY ").append(dialect.quoteIdentifier(idColumn.getColumnName())).append(" ASC");
            }
            return;
        }
        sb.append(" ORDER BY ");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Order o = orders.get(i);
            ColumnMetadata cm = def.findColumnByProperty(o.getField());
            String column = cm != null ? cm.getColumnName() : o.getField();
            sb.append(dialect.quoteIdentifier(column)).append(o.isAscending() ? " ASC" : " DESC");
        }
    }

    private void bindEntityParams(PreparedStatement ps, List<ColumnMetadata> columns, Object entity) throws SQLException {
        int index = 1;
        for (ColumnMetadata c : columns) {
            TypeBinders.bind(ps, index++, c.getValue(entity));
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        int index = 1;
        for (Object p : params) {
            TypeBinders.bind(ps, index++, p);
        }
    }

    private static List<Object> toList(Object[] array) {
        List<Object> list = new ArrayList<>();
        if (array != null) {
            for (Object o : array) {
                list.add(o);
            }
        }
        return list;
    }

    private static List<Object> singletonList(Object value) {
        List<Object> list = new ArrayList<>(1);
        list.add(value);
        return list;
    }

    private void recordElapsed(long start, String sql) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > MetricsCollector.getSlowQueryThresholdMs()) {
            MetricsCollector.getInstance().recordSlowQuery(sql, elapsed);
        }
    }
}
