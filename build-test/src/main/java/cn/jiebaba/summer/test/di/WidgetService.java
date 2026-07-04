package cn.jiebaba.summer.test.di;

import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.service.ServiceImpl;

/**
 * 故意不为 baseMapper 提供 setter：它必须由容器自动注入
 * （修复框架缺陷：ServiceImpl.baseMapper 此前从未被装配，迫使每个子类手写 setter）。
 */
@Service
public class WidgetService extends ServiceImpl<WidgetMapper, Widget> {
}
