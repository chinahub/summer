module summer.data {
    requires summer.core;
    requires java.sql;
    requires java.logging;

    exports cn.jiebaba.summer.data.annotation;
    exports cn.jiebaba.summer.data.metadata;
    exports cn.jiebaba.summer.data.conditions;
    exports cn.jiebaba.summer.data.mapper;
    exports cn.jiebaba.summer.data.page;
    exports cn.jiebaba.summer.data.service;
    exports cn.jiebaba.summer.data.support;
    exports cn.jiebaba.summer.data.dialect;
    exports cn.jiebaba.summer.data.transaction;
    exports cn.jiebaba.summer.data.datasource;
}
