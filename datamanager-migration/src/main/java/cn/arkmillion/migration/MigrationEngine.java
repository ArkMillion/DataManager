package cn.arkmillion.migration;

public interface MigrationEngine extends AutoCloseable {

    MigrationSummary migrate();

    String info();

    void validate();

    @Override
    default void close() {
    }
}
