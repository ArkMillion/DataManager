package cn.arkmillion.core.condition;

public final class PageParam {

    private final int pageNum;
    private final int pageSize;

    private PageParam(int pageNum, int pageSize) {
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static PageParam of(int pageNum, int pageSize) {
        return new PageParam(pageNum, pageSize);
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

    @Override
    public String toString() {
        return "PageParam{" + pageNum + "," + pageSize + "}";
    }
}
