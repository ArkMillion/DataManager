package cn.arkmillion.core.db;

public interface Subscription extends AutoCloseable {

    boolean isSubscribed();

    void unsubscribe();

    @Override
    void close();
}
