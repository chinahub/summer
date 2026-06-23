module summer.boot {
    requires summer.core;
    requires summer.web;
    requires summer.data;
    requires java.sql;
    requires java.logging;

    exports cn.jiebaba.summer.boot;
    exports cn.jiebaba.summer.boot.annotation;
    exports cn.jiebaba.summer.boot.data;
}
