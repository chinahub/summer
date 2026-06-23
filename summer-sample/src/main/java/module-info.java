module summer.sample {
    requires summer.boot;
    requires summer.web;
    requires summer.core;
    requires summer.data;
    requires java.sql;
    requires java.logging;

    opens cn.jiebaba.summer.sample to summer.core, summer.web, summer.data;
    opens cn.jiebaba.summer.sample.controller to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.service to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.model to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.config to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.advice to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.entity to summer.core, summer.web, summer.data;
    opens cn.jiebaba.summer.sample.mapper to summer.core, summer.data;
    opens cn.jiebaba.summer.sample.repository to summer.core, summer.web, summer.data;
    opens cn.jiebaba.summer.sample.task to summer.core;
    opens cn.jiebaba.summer.sample.aspect to summer.core;
    opens cn.jiebaba.summer.sample.websocket to summer.core, summer.web;
}
