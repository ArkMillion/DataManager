package cn.arkmillion.postgresql;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.PostgresConfig;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.factory.RelationalDBProvider;

import java.util.ArrayList;
import java.util.List;

public class PostgreSQLProvider implements RelationalDBProvider {

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public List<InstanceBinding<RelationalDB>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<RelationalDB>> bindings = new ArrayList<>();
        for (PostgresConfig cfg : config.getPostgresConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new PostgreSQLAdapter(cfg)));
        }
        return bindings;
    }
}
