package cn.arkmillion.core.condition;

import cn.arkmillion.core.enums.Operator;

import java.util.Collections;
import java.util.List;

public final class Criterion {

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
    private final List<Criterion> children;

    private Criterion(Kind kind, Connector connector, String field, Operator operator, Object value, List<Criterion> children) {
        this.kind = kind;
        this.connector = connector;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.children = children == null ? Collections.emptyList() : children;
    }

    static Criterion leaf(Connector connector, String field, Operator operator, Object value) {
        return new Criterion(Kind.LEAF, connector, field, operator, value, null);
    }

    static Criterion group(Connector connector, List<Criterion> children) {
        return new Criterion(Kind.GROUP, connector, null, null, null, children);
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

    public List<Criterion> getChildren() {
        return children;
    }
}
