package io.github.qifan777.server.mall.controller;

import java.util.List;

/** 通用分页返回。 */
public class Page<T> {
    private long total;
    private List<T> records;

    public Page() {}
    public Page(long total, List<T> records) { this.total = total; this.records = records; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
}