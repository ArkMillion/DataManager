package cn.arkmillion.core.filter;

import java.util.ArrayList;
import java.util.List;

public final class Update {

    public enum Op {
        SET,
        UNSET,
        INC,
        PUSH,
        PULL,
        SET_ON_INSERT
    }

    public static final class Entry {

        private final Op op;
        private final String key;
        private final Object value;

        Entry(Op op, String key, Object value) {
            this.op = op;
            this.key = key;
            this.value = value;
        }

        public Op getOp() {
            return op;
        }

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private Update() {
    }

    public static Update builder() {
        return new Update();
    }

    public Update set(String key, Object value) {
        entries.add(new Entry(Op.SET, key, value));
        return this;
    }

    public Update unset(String key) {
        entries.add(new Entry(Op.UNSET, key, null));
        return this;
    }

    public Update inc(String key, Number amount) {
        entries.add(new Entry(Op.INC, key, amount));
        return this;
    }

    public Update push(String key, Object value) {
        entries.add(new Entry(Op.PUSH, key, value));
        return this;
    }

    public Update pull(String key, Object value) {
        entries.add(new Entry(Op.PULL, key, value));
        return this;
    }

    public Update setOnInsert(String key, Object value) {
        entries.add(new Entry(Op.SET_ON_INSERT, key, value));
        return this;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
