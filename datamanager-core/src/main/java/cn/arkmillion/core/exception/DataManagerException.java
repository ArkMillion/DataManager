package cn.arkmillion.core.exception;

public class DataManagerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataManagerException(String message) {
        super(message);
    }

    public DataManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
