package cn.jiebaba.summer.data.service;

import cn.jiebaba.summer.data.mapper.BaseMapper;

public abstract class ServiceImpl<M extends BaseMapper<T>, T> implements IService<T> {

    protected M baseMapper;

    public void setBaseMapper(M baseMapper) { this.baseMapper = baseMapper; }

    @Override
    public M baseMapper() { return baseMapper; }
}
