package cn.arkmillion.core.db;

import cn.arkmillion.core.condition.Condition;
import cn.arkmillion.core.condition.PageParam;
import cn.arkmillion.core.condition.PageResult;

import java.util.List;
import java.util.Map;

public interface RelationalDB extends AutoCloseable {

    <T> int insert(T entity);

    <T> int batchInsert(List<T> entities);

    <T> int update(T entity);

    <T> int update(Class<T> clazz, Condition condition, Map<String, Object> updates);

    <T> int delete(Class<T> clazz, Condition condition);

    <T> int deleteById(Class<T> clazz, Object id);

    <T> List<T> select(Class<T> clazz, Condition condition);

    <T> T selectOne(Class<T> clazz, Condition condition);

    <T> T selectById(Class<T> clazz, Object id);

    <T> long count(Class<T> clazz, Condition condition);

    <T> PageResult<T> selectPage(Class<T> clazz, Condition condition, PageParam page);

    int execute(String sql, Object... params);

    <T> List<T> query(String sql, Class<T> clazz, Object... params);

    List<Map<String, Object>> queryMap(String sql, Object... params);

    void beginTransaction();

    void commit();

    void rollback();

    boolean isInTransaction();

    void syncSchema(Class<?> entityClass, cn.arkmillion.core.enums.SyncMode mode);

    void executeSqlFile(String filePath);

    void executeSqlFile(String filePath, Map<String, String> placeholders);

    @Override
    void close();
}
