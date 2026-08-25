package cn.arkmillion.core.condition;

import cn.arkmillion.core.enums.Operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Condition {

    protected final List<Criterion> criteria = new ArrayList<>();
    protected final List<Order> orders = new ArrayList<>();

    protected Condition() {
    }

    public static ConditionBuilder where(String field) {
        return new ConditionBuilder(field);
    }

    public static Condition empty() {
        return new Condition();
    }

    public List<Criterion> getCriteria() {
        return criteria;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public boolean isEmpty() {
        return criteria.isEmpty();
    }

    public Condition orderBy(String field, boolean ascending) {
        orders.add(new Order(field, ascending));
        return this;
    }

    @Override
    public String toString() {
        return "Condition{criteria=" + criteria.size() + ", orders=" + orders.size() + "}";
    }

    public static class ConditionBuilder extends Condition {

        private String currentField;
        private Criterion.Connector pendingConnector = Criterion.Connector.AND;

        protected ConditionBuilder(String field) {
            this.currentField = field;
        }

        public ConditionBuilder eq(Object value) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.EQ, value));
        }

        public ConditionBuilder ne(Object value) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.NE, value));
        }

        public ConditionBuilder gt(Object value) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.GT, value));
        }

        public ConditionBuilder gte(Object value) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.GTE, value));
        }

        public ConditionBuilder lt(Object value) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.LT, value));
        }

        public ConditionBuilder lte(Object value) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.LTE, value));
        }

        public ConditionBuilder like(String pattern) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.LIKE, pattern));
        }

        public ConditionBuilder in(Collection<?> values) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.IN, values));
        }

        public ConditionBuilder notIn(Collection<?> values) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.NOT_IN, values));
        }

        public ConditionBuilder between(Object start, Object end) {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.BETWEEN, new Object[]{start, end}));
        }

        public ConditionBuilder isNull() {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.IS_NULL, null));
        }

        public ConditionBuilder isNotNull() {
            return append(Criterion.leaf(pendingConnector, currentField, Operator.IS_NOT_NULL, null));
        }

        public ConditionBuilder and(String field) {
            this.currentField = field;
            this.pendingConnector = Criterion.Connector.AND;
            return this;
        }

        public ConditionBuilder or(String field) {
            this.currentField = field;
            this.pendingConnector = Criterion.Connector.OR;
            return this;
        }

        public ConditionBuilder and(Condition other) {
            return appendGroup(other, Criterion.Connector.AND);
        }

        public ConditionBuilder or(Condition other) {
            return appendGroup(other, Criterion.Connector.OR);
        }

        public Condition build() {
            return this;
        }

        private ConditionBuilder append(Criterion criterion) {
            criteria.add(criterion);
            pendingConnector = Criterion.Connector.AND;
            return this;
        }

        private ConditionBuilder appendGroup(Condition other, Criterion.Connector connector) {
            if (other != null && !other.getCriteria().isEmpty()) {
                criteria.add(Criterion.group(connector, other.getCriteria()));
            }
            pendingConnector = Criterion.Connector.AND;
            return this;
        }
    }
}
