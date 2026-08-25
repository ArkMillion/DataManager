package cn.arkmillion.core.db;

import cn.arkmillion.core.condition.PageParam;
import cn.arkmillion.core.condition.PageResult;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.filter.AggregationStage;
import cn.arkmillion.core.filter.Filter;
import cn.arkmillion.core.filter.Update;

import java.util.List;

public interface DocumentDB extends AutoCloseable {

    <T> void insert(T document);

    <T> void insertMany(List<T> documents);

    <T> List<T> find(Class<T> clazz, Filter filter);

    <T> T findOne(Class<T> clazz, Filter filter);

    <T> long update(Class<T> clazz, Filter filter, Update update);

    <T> long updateOne(Class<T> clazz, Filter filter, Update update);

    <T> long delete(Class<T> clazz, Filter filter);

    <T> long deleteOne(Class<T> clazz, Filter filter);

    <T> long count(Class<T> clazz, Filter filter);

    <T> PageResult<T> findPage(Class<T> clazz, Filter filter, PageParam page);

    <T> List<T> aggregate(Class<T> clazz, List<AggregationStage> stages);

    <T> void syncSchema(Class<T> clazz, SyncMode mode);

    @Override
    void close();
}
