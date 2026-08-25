package cn.arkmillion.redis;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.RedisConfig;
import cn.arkmillion.core.db.CacheManager;
import cn.arkmillion.core.factory.CacheProvider;
import cn.arkmillion.core.factory.InstanceBinding;

import java.util.ArrayList;
import java.util.List;

public class RedisProvider implements CacheProvider {

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public List<InstanceBinding<CacheManager>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<CacheManager>> bindings = new ArrayList<>();
        for (RedisConfig cfg : config.getRedisConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new RedisCacheManager(cfg)));
        }
        return bindings;
    }
}
