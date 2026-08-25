package cn.arkmillion.core.jdbc;

import cn.arkmillion.core.annotation.Transactional;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.exception.DataManagerException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TransactionProxy implements InvocationHandler {

    private final Object target;
    private final RelationalDB db;

    public TransactionProxy(Object target, RelationalDB db) {
        this.target = target;
        this.db = db;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method targetMethod;
        try {
            targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return method.invoke(target, args);
        }
        if (!targetMethod.isAnnotationPresent(Transactional.class)) {
            return invokeTarget(method, args);
        }
        boolean outerTransaction = db.isInTransaction();
        db.beginTransaction();
        try {
            Object result = invokeTarget(method, args);
            if (!outerTransaction) {
                db.commit();
            }
            return result;
        } catch (Throwable t) {
            if (!outerTransaction) {
                try {
                    db.rollback();
                } catch (RuntimeException rollbackError) {
                    t.addSuppressed(rollbackError);
                }
            }
            throw t;
        }
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(T target, RelationalDB db, Class<T> iface) {
        if (!iface.isInterface()) {
            throw new DataManagerException("TransactionProxy requires an interface: " + iface.getName());
        }
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                new TransactionProxy(target, db));
    }
}
