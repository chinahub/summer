package cn.jiebaba.summer.data.page;

import java.util.List;

public interface IPage<T> {
    List<T> records();
    long total();
    long size();
    long current();
    long pages();
    default long offset() { return (Math.max(current(), 1) - 1) * size(); }
    IPage<T> setRecords(List<T> records);
    IPage<T> setTotal(long total);
}
