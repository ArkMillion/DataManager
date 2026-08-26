package cn.arkmillion.core.mq;

public interface MessagingManager extends AutoCloseable {

    void send(String topic, String message);

    void send(String topic, String key, String message);

    <T> void sendObject(String topic, T obj);

    <T> void sendObject(String topic, String key, T obj);

    MessageSubscription subscribe(String topic, String groupId, MessagingListener listener);

    @Override
    void close();
}
