package cn.arkmillion.core.filter;

import cn.arkmillion.core.condition.Order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AggregationStage {

    public enum Kind {
        MATCH,
        SORT,
        SKIP,
        LIMIT,
        COUNT,
        GROUP,
        RAW
    }

    private final Kind kind;
    private final Filter filter;
    private final List<Order> orders;
    private final long number;
    private final String fieldName;
    private final String groupExpression;
    private final Map<String, String> accumulators;
    private final String rawJson;

    private AggregationStage(Kind kind, Filter filter, List<Order> orders, long number,
                             String fieldName, String groupExpression, Map<String, String> accumulators, String rawJson) {
        this.kind = kind;
        this.filter = filter;
        this.orders = orders;
        this.number = number;
        this.fieldName = fieldName;
        this.groupExpression = groupExpression;
        this.accumulators = accumulators;
        this.rawJson = rawJson;
    }

    public static AggregationStage match(Filter filter) {
        return new AggregationStage(Kind.MATCH, filter, null, 0, null, null, null, null);
    }

    public static AggregationStage sort(List<Order> orders) {
        return new AggregationStage(Kind.SORT, null, new ArrayList<>(orders), 0, null, null, null, null);
    }

    public static AggregationStage skip(long count) {
        return new AggregationStage(Kind.SKIP, null, null, count, null, null, null, null);
    }

    public static AggregationStage limit(long count) {
        return new AggregationStage(Kind.LIMIT, null, null, count, null, null, null, null);
    }

    public static AggregationStage count(String outputField) {
        return new AggregationStage(Kind.COUNT, null, null, 0, outputField, null, null, null);
    }

    public static AggregationStage group(String groupExpression, Map<String, String> accumulatorJson) {
        return new AggregationStage(Kind.GROUP, null, null, 0, null, groupExpression, new LinkedHashMap<>(accumulatorJson), null);
    }

    public static AggregationStage raw(String stageJson) {
        return new AggregationStage(Kind.RAW, null, null, 0, null, null, null, stageJson);
    }

    public Kind getKind() {
        return kind;
    }

    public Filter getFilter() {
        return filter;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public long getNumber() {
        return number;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getGroupExpression() {
        return groupExpression;
    }

    public Map<String, String> getAccumulators() {
        return accumulators;
    }

    public String getRawJson() {
        return rawJson;
    }
}
