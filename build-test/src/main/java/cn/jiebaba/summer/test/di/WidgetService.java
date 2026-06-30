package cn.jiebaba.summer.test.di;

import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.service.ServiceImpl;

/**
 * Deliberately provides NO setter for baseMapper: it must be auto-injected by
 * the container (the fix for the framework issue where ServiceImpl.baseMapper
 * was never wired, forcing every subclass to hand-write a setter).
 */
@Service
public class WidgetService extends ServiceImpl<WidgetMapper, Widget> {
}
