package cn.arkmillion.core.condition;

public final class Order {

    private final String field;
    private final boolean ascending;

    public Order(String field, boolean ascending) {
        this.field = field;
        this.ascending = ascending;
    }

    public String getField() {
        return field;
    }

    public boolean isAscending() {
        return ascending;
    }
}
