package cn.arkmillion.core.mq;

@FunctionalInterface
public interface MessagingListener {

    void onMessage(String topic, String key, String message);
}
