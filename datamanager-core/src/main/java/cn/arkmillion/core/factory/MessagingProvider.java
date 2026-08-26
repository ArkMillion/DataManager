package cn.arkmillion.core.factory;

import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.mq.MessagingManager;

import java.util.List;

public interface MessagingProvider {

    String name();

    List<InstanceBinding<MessagingManager>> createInstances(DataManagerConfig config);
}
