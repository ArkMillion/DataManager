package cn.arkmillion.sqlite;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.SQLiteConfig;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.factory.RelationalDBProvider;

import java.util.ArrayList;
import java.util.List;

public class SQLiteProvider implements RelationalDBProvider {

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public List<InstanceBinding<RelationalDB>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<RelationalDB>> bindings = new ArrayList<>();
        for (SQLiteConfig cfg : config.getSQLiteConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new SQLiteAdapter(cfg)));
        }
        return bindings;
    }
}
