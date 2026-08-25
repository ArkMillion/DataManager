package cn.arkmillion.core.factory;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.DocumentDB;

import java.util.List;

public interface DocumentDBProvider {

    String name();

    List<InstanceBinding<DocumentDB>> createInstances(DataManagerConfig config);
}
