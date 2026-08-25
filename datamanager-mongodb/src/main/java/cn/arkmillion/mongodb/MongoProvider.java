package cn.arkmillion.mongodb;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.MongoConfig;
import cn.arkmillion.core.db.DocumentDB;
import cn.arkmillion.core.factory.DocumentDBProvider;
import cn.arkmillion.core.factory.InstanceBinding;

import java.util.ArrayList;
import java.util.List;

public class MongoProvider implements DocumentDBProvider {

    @Override
    public String name() {
        return "mongo";
    }

    @Override
    public List<InstanceBinding<DocumentDB>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<DocumentDB>> bindings = new ArrayList<>();
        for (MongoConfig cfg : config.getMongoConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new MongoAdapter(cfg)));
        }
        return bindings;
    }
}
