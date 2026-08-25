package cn.arkmillion.core.condition;

import java.util.Collections;
import java.util.List;

public final class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final int pageNum;
    private final int pageSize;

    private PageResult(List<T> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, long total, PageParam param) {
        return new PageResult<>(records, total, param.getPageNum(), param.getPageSize());
    }

    public static <T> PageResult<T> empty(PageParam param) {
        return new PageResult<>(Collections.emptyList(), 0, param.getPageNum(), param.getPageSize());
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (total + pageSize - 1) / pageSize;
    }

    public boolean hasNext() {
        return pageNum < getPages();
    }
}
