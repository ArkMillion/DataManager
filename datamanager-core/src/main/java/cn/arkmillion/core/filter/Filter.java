package cn.arkmillion.core.filter;

import cn.arkmillion.core.enums.Operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Filter {

    protected final List<FilterCriterion> criteria = new ArrayList<>();

    protected Filter() {
    }

    public static FilterBuilder where(String field) {
        return new FilterBuilder(field);
    }

    public static Filter empty() {
        return new Filter();
    }

    public List<FilterCriterion> getCriteria() {
        return criteria;
    }

    public boolean isEmpty() {
        return criteria.isEmpty();
    }

    @Override
    public String toString() {
        return "Filter{criteria=" + criteria.size() + "}";
    }

    public static class FilterBuilder extends Filter {

        private String currentField;
        private FilterCriterion.Connector pendingConnector = FilterCriterion.Connector.AND;

        protected FilterBuilder(String field) {
            this.currentField = field;
        }

        public FilterBuilder eq(Object value) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.EQ, value));
        }

        public FilterBuilder ne(Object value) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.NE, value));
        }

        public FilterBuilder gt(Object value) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.GT, value));
        }

        public FilterBuilder gte(Object value) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.GTE, value));
        }

        public FilterBuilder lt(Object value) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.LT, value));
        }

        public FilterBuilder lte(Object value) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.LTE, value));
        }

        public FilterBuilder like(String pattern) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.LIKE, pattern));
        }

        public FilterBuilder in(Collection<?> values) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.IN, values));
        }

        public FilterBuilder nin(Collection<?> values) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.NOT_IN, values));
        }

        public FilterBuilder between(Object start, Object end) {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.BETWEEN, new Object[]{start, end}));
        }

        public FilterBuilder isNull() {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.IS_NULL, null));
        }

        public FilterBuilder isNotNull() {
            return append(FilterCriterion.leaf(pendingConnector, currentField, Operator.IS_NOT_NULL, null));
        }

        public FilterBuilder and(String field) {
            this.currentField = field;
            this.pendingConnector = FilterCriterion.Connector.AND;
            return this;
        }

        public FilterBuilder or(String field) {
            this.currentField = field;
            this.pendingConnector = FilterCriterion.Connector.OR;
            return this;
        }

        public FilterBuilder and(Filter other) {
            return appendGroup(other, FilterCriterion.Connector.AND);
        }

        public FilterBuilder or(Filter other) {
            return appendGroup(other, FilterCriterion.Connector.OR);
        }

        public Filter build() {
            return this;
        }

        private FilterBuilder append(FilterCriterion criterion) {
            criteria.add(criterion);
            pendingConnector = FilterCriterion.Connector.AND;
            return this;
        }

        private FilterBuilder appendGroup(Filter other, FilterCriterion.Connector connector) {
            if (other != null && !other.getCriteria().isEmpty()) {
                criteria.add(FilterCriterion.group(connector, other.getCriteria()));
            }
            pendingConnector = FilterCriterion.Connector.AND;
            return this;
        }
    }
}
