package cn.arkmillion.core.factory;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.CacheManager;

import java.util.List;

public interface CacheProvider {

    String name();

    List<InstanceBinding<CacheManager>> createInstances(DataManagerConfig config);
}
