package cn.arkmillion.core.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConnectionMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionMonitor.class);

    private static final ConnectionMonitor INSTANCE = new ConnectionMonitor();

    private final List<ConnectionStateListener> listeners = new CopyOnWriteArrayList<>();

    private ConnectionMonitor() {
    }

    public static ConnectionMonitor getInstance() {
        return INSTANCE;
    }

    public void addListener(ConnectionStateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ConnectionStateListener listener) {
        listeners.remove(listener);
    }

    public void notifyConnected(String poolName) {
        LOG.debug("Connection pool '{}' connected", poolName);
        for (ConnectionStateListener l : listeners) {
            try {
                l.onConnected(poolName);
            } catch (RuntimeException e) {
                LOG.warn("Listener {} threw on onConnected: {}", l, e.getMessage());
            }
        }
    }

    public void notifyDisconnected(String poolName, Throwable cause) {
        LOG.warn("Connection pool '{}' disconnected: {}", poolName, cause.getMessage());
        for (ConnectionStateListener l : listeners) {
            try {
                l.onDisconnected(poolName, cause);
            } catch (RuntimeException e) {
                LOG.warn("Listener {} threw on onDisconnected: {}", l, e.getMessage());
            }
        }
    }

    public void notifyError(String poolName, SQLException ex) {
        LOG.warn("Connection error on pool '{}': {}", poolName, ex.getMessage());
        for (ConnectionStateListener l : listeners) {
            try {
                l.onConnectionError(poolName, ex);
            } catch (RuntimeException e) {
                LOG.warn("Listener {} threw on onConnectionError: {}", l, e.getMessage());
            }
        }
    }
}
