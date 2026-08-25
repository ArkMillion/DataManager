package cn.arkmillion.core.metadata;

import cn.arkmillion.core.enums.IndexType;

import java.util.Collections;
import java.util.List;

public final class IndexMetadata {

    private final String name;
    private final List<String> columns;
    private final boolean unique;
    private final IndexType type;

    public IndexMetadata(String name, List<String> columns, boolean unique, IndexType type) {
        this.name = name;
        this.columns = Collections.unmodifiableList(columns);
        this.unique = unique;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean isUnique() {
        return unique;
    }

    public IndexType getType() {
        return type;
    }
}
