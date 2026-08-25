package cn.arkmillion.core.monitor;

import java.sql.SQLException;

public interface ConnectionStateListener {

    void onConnected(String poolName);

    void onDisconnected(String poolName, Throwable cause);

    void onConnectionError(String poolName, SQLException ex);
}
