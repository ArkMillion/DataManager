package cn.arkmillion.core.db;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CacheManager extends AutoCloseable {

    void set(String key, String value);

    void set(String key, String value, Duration ttl);

    void set(String key, String value, long seconds);

    String get(String key);

    void del(String key);

    boolean exists(String key);

    boolean expire(String key, long seconds);

    long ttl(String key);

    void hSet(String key, String field, String value);

    String hGet(String key, String field);

    Map<String, String> hGetAll(String key);

    void hDel(String key, String... fields);

    boolean hExists(String key, String field);

    void lPush(String key, String... values);

    void rPush(String key, String... values);

    String lPop(String key);

    String rPop(String key);

    List<String> lRange(String key, long start, long end);

    void sAdd(String key, String... members);

    void sRem(String key, String... members);

    Set<String> sMembers(String key);

    boolean sIsMember(String key, String member);

    void zAdd(String key, double score, String member);

    void zAdd(String key, Map<String, Double> scoreMembers);

    Set<String> zRange(String key, long start, long end);

    Set<String> zRangeByScore(String key, double min, double max);

    long zRem(String key, String... members);

    <T> void setObject(String key, T obj);

    <T> void setObject(String key, T obj, Duration ttl);

    <T> T getObject(String key, Class<T> clazz);

    <T> T getObject(String key, TypeReference<T> typeRef);

    void mSet(Map<String, String> keyValues);

    List<String> mGet(String... keys);

    void del(String... keys);

    boolean tryLock(String lockKey, String requestId, long expireSeconds);

    boolean releaseLock(String lockKey, String requestId);

    Set<String> keys(String pattern);

    long dbSize();

    void flushDb();

    void flushAll();

    @Override
    void close();
}
