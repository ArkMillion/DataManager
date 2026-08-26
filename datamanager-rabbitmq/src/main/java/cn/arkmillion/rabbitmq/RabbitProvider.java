package cn.arkmillion.rabbitmq;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.RabbitConfig;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.factory.MessagingProvider;
import cn.arkmillion.core.mq.MessagingManager;

import java.util.ArrayList;
import java.util.List;

public class RabbitProvider implements MessagingProvider {

    @Override
    public String name() {
        return "rabbit";
    }

    @Override
    public List<InstanceBinding<MessagingManager>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<MessagingManager>> bindings = new ArrayList<>();
        for (RabbitConfig cfg : config.getRabbitConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new RabbitMessaging(cfg)));
        }
        return bindings;
    }
}
