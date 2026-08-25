package cn.arkmillion.redis;

import cn.arkmillion.core.config.PlaceholderResolver;
import cn.arkmillion.core.config.RedisConfig;
import cn.arkmillion.core.db.CacheManager;
import cn.arkmillion.core.exception.DataManagerException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisCacheManager implements CacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCacheManager.class);

    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RedisConfig config;
    private final JedisPool pool;
    private final ObjectMapper mapper;

    public RedisCacheManager(RedisConfig config) {
        this.config = config;
        String password = config.getPassword();
        if (password != null && !password.isEmpty()) {
            password = PlaceholderResolver.resolve(password);
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());
        poolConfig.setTestWhileIdle(true);

        this.pool = new JedisPool(poolConfig, buildUri(config, password));
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static String buildUri(RedisConfig config, String resolvedPassword) {
        String scheme = config.isSsl() ? "rediss" : "redis";
        String host = PlaceholderResolver.resolve(config.getHost());
        StringBuilder sb = new StringBuilder(scheme).append("://");
        if (resolvedPassword != null && !resolvedPassword.isEmpty()) {
            sb.append(':').append(resolvedPassword).append('@');
        }
        sb.append(host).append(':').append(config.getPort()).append('/').append(config.getDatabase());
        return sb.toString();
    }

    private <T> T execute(Function<Jedis, T> action) {
        try (Jedis jedis = pool.getResource()) {
            return action.apply(jedis);
        } catch (DataManagerException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DataManagerException("Redis operation failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    interface Function<J, R> {

        R apply(J input);
    }

    @Override
    public void set(String key, String value) {
        execute(j -> j.set(key, value));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        long seconds = ttl == null ? 0 : ttl.getSeconds();
        set(key, value, seconds);
    }

    @Override
    public void set(String key, String value, long seconds) {
        if (seconds > 0) {
            execute(j -> j.setex(key, seconds, value));
        } else {
            execute(j -> j.set(key, value));
        }
    }

    @Override
    public String get(String key) {
        return execute(j -> j.get(key));
    }

    @Override
    public void del(String key) {
        execute(j -> j.del(key));
    }

    @Override
    public boolean exists(String key) {
        return execute(j -> j.exists(key));
    }

    @Override
    public boolean expire(String key, long seconds) {
        Long result = execute(j -> j.expire(key, (int) seconds));
        return result != null && result == 1L;
    }

    @Override
    public long ttl(String key) {
        return execute(j -> j.ttl(key));
    }

    @Override
    public void hSet(String key, String field, String value) {
        execute(j -> j.hset(key, field, value));
    }

    @Override
    public String hGet(String key, String field) {
        return execute(j -> j.hget(key, field));
    }

    @Override
    public Map<String, String> hGetAll(String key) {
        Map<String, String> raw = execute(j -> j.hgetAll(key));
        return raw == null ? Collections.emptyMap() : new HashMap<>(raw);
    }

    @Override
    public void hDel(String key, String... fields) {
        execute(j -> j.hdel(key, fields));
    }

    @Override
    public boolean hExists(String key, String field) {
        return execute(j -> j.hexists(key, field));
    }

    @Override
    public void lPush(String key, String... values) {
        execute(j -> j.lpush(key, values));
    }

    @Override
    public void rPush(String key, String... values) {
        execute(j -> j.rpush(key, values));
    }

    @Override
    public String lPop(String key) {
        return execute(j -> j.lpop(key));
    }

    @Override
    public String rPop(String key) {
        return execute(j -> j.rpop(key));
    }

    @Override
    public List<String> lRange(String key, long start, long end) {
        List<String> raw = execute(j -> j.lrange(key, start, end));
        return raw == null ? Collections.emptyList() : new ArrayList<>(raw);
    }

    @Override
    public void sAdd(String key, String... members) {
        execute(j -> j.sadd(key, members));
    }

    @Override
    public void sRem(String key, String... members) {
        execute(j -> j.srem(key, members));
    }

    @Override
    public Set<String> sMembers(String key) {
        Set<String> raw = execute(j -> j.smembers(key));
        return raw == null ? Collections.emptySet() : new HashSet<>(raw);
    }

    @Override
    public boolean sIsMember(String key, String member) {
        return execute(j -> j.sismember(key, member));
    }

    @Override
    public void zAdd(String key, double score, String member) {
        execute(j -> j.zadd(key, score, member));
    }

    @Override
    public void zAdd(String key, Map<String, Double> scoreMembers) {
        execute(j -> j.zadd(key, scoreMembers));
    }

    @Override
    public Set<String> zRange(String key, long start, long end) {
        List<String> raw = execute(j -> j.zrange(key, start, end));
        return raw == null ? Collections.emptySet() : new LinkedHashSet<>(raw);
    }

    @Override
    public Set<String> zRangeByScore(String key, double min, double max) {
        List<String> raw = execute(j -> j.zrangeByScore(key, min, max));
        return raw == null ? Collections.emptySet() : new LinkedHashSet<>(raw);
    }

    @Override
    public long zRem(String key, String... members) {
        return execute(j -> j.zrem(key, members));
    }

    @Override
    public <T> void setObject(String key, T obj) {
        set(key, serialize(obj));
    }

    @Override
    public <T> void setObject(String key, T obj, Duration ttl) {
        set(key, serialize(obj), ttl);
    }

    @Override
    public <T> T getObject(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new DataManagerException("Failed to deserialize object for key '" + key + "'", e);
        }
    }

    @Override
    public <T> T getObject(String key, TypeReference<T> typeRef) {
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new DataManagerException("Failed to deserialize object for key '" + key + "'", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new DataManagerException("Failed to serialize object", e);
        }
    }

    @Override
    public void mSet(Map<String, String> keyValues) {
        if (keyValues == null || keyValues.isEmpty()) {
            return;
        }
        execute(j -> j.mset(toFlatArray(keyValues)));
    }

    private String[] toFlatArray(Map<String, String> keyValues) {
        String[] flat = new String[keyValues.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> e : keyValues.entrySet()) {
            flat[i++] = e.getKey();
            flat[i++] = e.getValue();
        }
        return flat;
    }

    @Override
    public List<String> mGet(String... keys) {
        List<String> values = execute(j -> j.mget(keys));
        return values == null ? Collections.emptyList() : values;
    }

    @Override
    public void del(String... keys) {
        if (keys.length == 0) {
            return;
        }
        execute(j -> j.del(keys));
    }

    @Override
    public boolean tryLock(String lockKey, String requestId, long expireSeconds) {
        String result = execute(j -> j.set(lockKey, requestId,
                SetParams.setParams().nx().ex((int) Math.max(1, expireSeconds))));
        return "OK".equals(result);
    }

    @Override
    public boolean releaseLock(String lockKey, String requestId) {
        Object result = execute(j -> j.eval(RELEASE_LOCK_LUA,
                Collections.singletonList(lockKey),
                Collections.singletonList(requestId)));
        return result instanceof Long && (Long) result == 1L;
    }

    @Override
    public Set<String> keys(String pattern) {
        Set<String> raw = execute(j -> j.keys(pattern));
        return raw == null ? Collections.emptySet() : new HashSet<>(raw);
    }

    @Override
    public long dbSize() {
        return execute(Jedis::dbSize);
    }

    @Override
    public void flushDb() {
        LOG.warn("FLUSHDB executed against {}:{}", config.getHost(), config.getPort());
        execute(Jedis::flushDB);
    }

    @Override
    public void flushAll() {
        LOG.warn("FLUSHALL executed against {}:{}", config.getHost(), config.getPort());
        execute(Jedis::flushAll);
    }

    @Override
    public void close() {
        pool.close();
    }

    public RedisConfig getConfig() {
        return config;
    }
}
