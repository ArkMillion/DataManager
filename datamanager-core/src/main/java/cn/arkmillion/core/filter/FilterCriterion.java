package cn.arkmillion.core.filter;

import cn.arkmillion.core.enums.Operator;

import java.util.Collections;
import java.util.List;

public final class FilterCriterion {

    public enum Kind {
        LEAF,
        GROUP
    }

    public enum Connector {
        AND,
        OR
    }

    private final Kind kind;
    private final Connector connector;
    private final String field;
    private final Operator operator;
    private final Object value;
    private final List<FilterCriterion> children;

    private FilterCriterion(Kind kind, Connector connector, String field, Operator operator, Object value, List<FilterCriterion> children) {
        this.kind = kind;
        this.connector = connector;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.children = children == null ? Collections.emptyList() : children;
    }

    static FilterCriterion leaf(Connector connector, String field, Operator operator, Object value) {
        return new FilterCriterion(Kind.LEAF, connector, field, operator, value, null);
    }

    static FilterCriterion group(Connector connector, List<FilterCriterion> children) {
        return new FilterCriterion(Kind.GROUP, connector, null, null, null, children);
    }

    public Kind getKind() {
        return kind;
    }

    public Connector getConnector() {
        return connector;
    }

    public String getField() {
        return field;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    public List<FilterCriterion> getChildren() {
        return children;
    }
}
