package cn.arkmillion.mysql;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.MySQLConfig;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.factory.RelationalDBProvider;

import java.util.ArrayList;
import java.util.List;

public class MySQLProvider implements RelationalDBProvider {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public List<InstanceBinding<RelationalDB>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<RelationalDB>> bindings = new ArrayList<>();
        for (MySQLConfig cfg : config.getMySQLConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new MySQLAdapter(cfg)));
        }
        return bindings;
    }
}
