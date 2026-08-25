package cn.arkmillion.core.schema;

public final class SchemaSyncPolicy {

    private static volatile boolean requireConfirm = false;
    private static final ThreadLocal<Boolean> CONFIRMED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SchemaSyncPolicy() {
    }

    public static void setRequireConfirm(boolean value) {
        requireConfirm = value;
    }

    public static boolean isRequireConfirm() {
        return requireConfirm;
    }

    public static void confirmDangerousOperation() {
        CONFIRMED.set(Boolean.TRUE);
    }

    public static void clearConfirmation() {
        CONFIRMED.set(Boolean.FALSE);
    }

    static void checkAllowed(cn.arkmillion.core.enums.SyncMode mode) {
        boolean dangerous = mode == cn.arkmillion.core.enums.SyncMode.UPDATE
                || mode == cn.arkmillion.core.enums.SyncMode.DROP_CREATE;
        if (dangerous && requireConfirm && !CONFIRMED.get()) {
            throw new cn.arkmillion.core.exception.DataManagerException(
                    "Dangerous schema operation " + mode + " is blocked by SchemaSyncPolicy. "
                            + "Call SchemaSyncPolicy.confirmDangerousOperation() to allow it once.");
        }
    }
}
