package cn.jiebaba.summer.data.page;

import java.util.ArrayList;
import java.util.List;

public class Page<T> implements IPage<T> {
    private List<T> records = new ArrayList<>();
    private long total = 0;
    private final long size;
    private final long current;

    public Page(long current, long size) {
        this.current = current < 1 ? 1 : current;
        this.size = size < 1 ? 10 : size;
    }

    @Override public List<T> records() { return records; }
    @Override public long total() { return total; }
    @Override public long size() { return size; }
    @Override public long current() { return current; }
    @Override public long pages() { return size <= 0 ? 0 : (total + size - 1) / size; }

    @Override public IPage<T> setRecords(List<T> records) { this.records = records; return this; }
    @Override public IPage<T> setTotal(long total) { this.total = total; return this; }

    public long offset() { return (current - 1) * size; }
}
