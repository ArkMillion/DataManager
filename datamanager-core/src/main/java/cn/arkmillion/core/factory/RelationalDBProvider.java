package cn.arkmillion.core.factory;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.RelationalDB;

import java.util.List;

public interface RelationalDBProvider {

    String name();

    List<InstanceBinding<RelationalDB>> createInstances(DataManagerConfig config);
}
