package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.sample.entity.Widget;
import cn.jiebaba.summer.sample.repository.WidgetService;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.ResponseStatus;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/widgets")
public class WidgetController {

    @Autowired
    private WidgetService widgetService;

    @PostMapping
    @ResponseStatus(201)
    public Widget create(@Valid @RequestBody Widget widget) {
        widgetService.save(widget);
        return widget;
    }

    @GetMapping("/{id}")
    public Widget get(@PathVariable Long id) {
        return widgetService.getById(id);
    }

    @GetMapping
    public List<Widget> list() {
        return widgetService.list();
    }
}