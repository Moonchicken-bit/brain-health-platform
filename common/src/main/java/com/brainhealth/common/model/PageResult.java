package com.brainhealth.common.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Generic paginated result for list endpoints.
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long page;
    private long size;
    private long total;
    private long totalPages;
    private List<T> records;

    public PageResult() {}

    public PageResult(long page, long size, long total, long totalPages, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
        this.records = records;
    }

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getTotalPages() { return totalPages; }
    public void setTotalPages(long totalPages) { this.totalPages = totalPages; }
    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(page, size, 0, 0, Collections.emptyList());
    }

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        long totalPages = size > 0 ? (total + size - 1) / size : 0;
        return new PageResult<>(page, size, total, totalPages,
                records != null ? records : Collections.emptyList());
    }
}
