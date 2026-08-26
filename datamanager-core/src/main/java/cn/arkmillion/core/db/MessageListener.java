package cn.arkmillion.core.db;

@FunctionalInterface
public interface MessageListener {

    void onMessage(String channel, String message);
}
