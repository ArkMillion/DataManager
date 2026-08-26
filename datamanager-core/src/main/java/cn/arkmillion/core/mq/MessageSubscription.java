package cn.arkmillion.core.mq;

public interface MessageSubscription extends AutoCloseable {

    boolean isActive();

    void unsubscribe();

    @Override
    void close();
}
