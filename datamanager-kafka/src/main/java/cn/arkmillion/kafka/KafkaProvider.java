package cn.arkmillion.kafka;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.KafkaConfig;
import cn.arkmillion.core.factory.InstanceBinding;
import cn.arkmillion.core.factory.MessagingProvider;
import cn.arkmillion.core.mq.MessagingManager;

import java.util.ArrayList;
import java.util.List;

public class KafkaProvider implements MessagingProvider {

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public List<InstanceBinding<MessagingManager>> createInstances(DataManagerConfig config) {
        List<InstanceBinding<MessagingManager>> bindings = new ArrayList<>();
        for (KafkaConfig cfg : config.getKafkaConfigs()) {
            bindings.add(InstanceBinding.of(
                    InstanceBinding.resolveAlias(cfg.getAlias(), name()),
                    new KafkaMessaging(cfg)));
        }
        return bindings;
    }
}
