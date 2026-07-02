package cn.jiebaba.summer.sample.repository;

import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.service.ServiceImpl;
import cn.jiebaba.summer.sample.entity.Widget;
import cn.jiebaba.summer.sample.mapper.WidgetMapper;

@Service
public class WidgetServiceImpl extends ServiceImpl<WidgetMapper, Widget> implements WidgetService {
}