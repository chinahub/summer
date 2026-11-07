-- =============================================================================
-- summer-admin 数据库初始化脚本（设计 §6.2，共 15 张表）
--
-- 特性：
--   1. 可重复执行：CREATE DATABASE/TABLE IF NOT EXISTS，种子数据使用 INSERT IGNORE
--      带固定主键，重跑既不报错也不覆盖已有业务修改；
--   2. 密码仅保存 BCrypt 哈希（admin/admin123、user/user123，仅用于本地首次登录）；
--   3. 核心实体带 del_flag 逻辑删除，关系表与日志表物理删除、不建逻辑删除字段。
--
-- 测试环境说明：测试执行器会跳过本文件头部的 CREATE DATABASE / USE 语句，
-- 在 summer_admin_test 库上重放其余全部语句（见 MySqlTestDatabase）。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS summer_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE summer_admin;

-- -----------------------------------------------------------------------------
-- 部门表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dept (
    dept_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    parent_id   BIGINT       NULL     DEFAULT NULL COMMENT '父部门ID',
    ancestors   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级列表，逗号分隔',
    dept_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '部门名称',
    order_num   INT          NOT NULL DEFAULT 0 COMMENT '显示排序',
    leader      VARCHAR(20)  NULL     DEFAULT NULL COMMENT '负责人',
    phone       VARCHAR(20)  NULL     DEFAULT NULL COMMENT '联系电话',
    email       VARCHAR(50)  NULL     DEFAULT NULL COMMENT '邮箱',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME     NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (dept_id),
    UNIQUE KEY uk_sys_dept_parent_name (parent_id, dept_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门表';

-- -----------------------------------------------------------------------------
-- 用户表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    user_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    dept_id     BIGINT       NULL     DEFAULT NULL COMMENT '部门ID',
    user_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '登录名',
    nick_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    email       VARCHAR(100) NULL     DEFAULT '' COMMENT '邮箱',
    phonenumber VARCHAR(20)  NULL     DEFAULT '' COMMENT '手机号',
    sex         CHAR(1)      NULL     DEFAULT '2' COMMENT '性别（0男 1女 2未知）',
    avatar      VARCHAR(255) NULL     DEFAULT '' COMMENT '头像地址',
    password    VARCHAR(100) NOT NULL DEFAULT '' COMMENT '密码 BCrypt 哈希',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    login_ip    VARCHAR(128) NULL     DEFAULT '' COMMENT '最后登录IP',
    login_date  DATETIME     NULL     DEFAULT NULL COMMENT '最后登录时间',
    create_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME     NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_name (user_name),
    KEY idx_sys_user_dept (dept_id),
    CONSTRAINT fk_sys_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- -----------------------------------------------------------------------------
-- 岗位表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_post (
    post_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    post_code   VARCHAR(64) NOT NULL DEFAULT '' COMMENT '岗位编码',
    post_name   VARCHAR(64) NOT NULL DEFAULT '' COMMENT '岗位名称',
    post_sort   INT         NOT NULL DEFAULT 0 COMMENT '显示排序',
    status      CHAR(1)     NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64) NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME    NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64) NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME    NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (post_id),
    UNIQUE KEY uk_sys_post_code (post_code),
    UNIQUE KEY uk_sys_post_name (post_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '岗位表';

-- -----------------------------------------------------------------------------
-- 用户与岗位关联表（物理删除）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_post (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    post_id BIGINT NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (user_id, post_id),
    KEY idx_sys_user_post_post (post_id),
    CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id),
    CONSTRAINT fk_up_post FOREIGN KEY (post_id) REFERENCES sys_post (post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户岗位关联表';

-- -----------------------------------------------------------------------------
-- 角色表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    role_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_name   VARCHAR(64) NOT NULL DEFAULT '' COMMENT '角色名称',
    role_key    VARCHAR(64) NOT NULL DEFAULT '' COMMENT '权限字符',
    role_sort   INT         NOT NULL DEFAULT 0 COMMENT '显示排序',
    data_scope  CHAR(1)     NOT NULL DEFAULT '1' COMMENT '数据范围（1全部 2自定义 3本部门 4本部门及以下 5仅本人）',
    status      CHAR(1)     NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64) NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME    NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64) NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME    NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_sys_role_key (role_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色表';

-- -----------------------------------------------------------------------------
-- 用户与角色关联表（物理删除）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联表';

-- -----------------------------------------------------------------------------
-- 菜单权限表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    menu_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    menu_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '菜单名称',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID',
    order_num   INT          NOT NULL DEFAULT 0 COMMENT '显示排序',
    path        VARCHAR(200) NULL     DEFAULT '' COMMENT '路由地址',
    component   VARCHAR(255) NULL     DEFAULT NULL COMMENT '组件路径',
    query       VARCHAR(255) NULL     DEFAULT NULL COMMENT '路由参数',
    is_frame    INT          NOT NULL DEFAULT 1 COMMENT '是否外链（1否 0是）',
    is_cache    INT          NOT NULL DEFAULT 0 COMMENT '是否缓存（0缓存 1不缓存）',
    menu_type   CHAR(1)      NOT NULL DEFAULT '' COMMENT '类型（M目录 C菜单 F按钮）',
    visible     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '显示状态（0显示 1隐藏）',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    perms       VARCHAR(100) NULL     DEFAULT NULL COMMENT '权限标识',
    icon        VARCHAR(100) NULL     DEFAULT '#' COMMENT '图标',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME     NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (menu_id),
    UNIQUE KEY uk_sys_menu_parent_name (parent_id, menu_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单权限表';

-- -----------------------------------------------------------------------------
-- 角色与菜单关联表（物理删除）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id),
    CONSTRAINT fk_rm_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id),
    CONSTRAINT fk_rm_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色菜单关联表';

-- -----------------------------------------------------------------------------
-- 角色与部门关联表（自定义数据范围，物理删除）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_dept (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID',
    PRIMARY KEY (role_id, dept_id),
    KEY idx_sys_role_dept_dept (dept_id),
    CONSTRAINT fk_rd_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id),
    CONSTRAINT fk_rd_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色部门关联表';

-- -----------------------------------------------------------------------------
-- 字典类型表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_type (
    dict_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    dict_name   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典名称',
    dict_type   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典类型',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME     NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (dict_id),
    UNIQUE KEY uk_sys_dict_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型表';

-- -----------------------------------------------------------------------------
-- 字典数据表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_data (
    dict_code   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    dict_sort   INT          NOT NULL DEFAULT 0 COMMENT '字典排序',
    dict_label  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典标签',
    dict_value  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典键值',
    dict_type   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典类型',
    css_class   VARCHAR(100) NULL     DEFAULT NULL COMMENT '样式属性',
    list_class  VARCHAR(100) NULL     DEFAULT NULL COMMENT '回显样式',
    is_default  CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    create_time DATETIME     NULL     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    update_time DATETIME     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (dict_code),
    KEY idx_sys_dict_data_type (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典数据表';

-- -----------------------------------------------------------------------------
-- 系统参数表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
    config_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '参数主键',
    config_name   VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '参数名称',
    config_key    VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '参数键名',
    config_value  VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '参数键值',
    config_type   CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
    status        CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    del_flag      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    create_by     VARCHAR(64)   NULL     DEFAULT '' COMMENT '创建者',
    create_time   DATETIME      NULL     DEFAULT NULL COMMENT '创建时间',
    update_by     VARCHAR(64)   NULL     DEFAULT '' COMMENT '更新者',
    update_time   DATETIME      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统参数表';

-- -----------------------------------------------------------------------------
-- 通知公告表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_notice (
    notice_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    notice_title   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '公告标题',
    notice_type    CHAR(1)      NOT NULL DEFAULT '' COMMENT '公告类型（1通知 2公告）',
    notice_content VARCHAR(2000) NULL    DEFAULT NULL COMMENT '公告内容',
    status         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1关闭）',
    create_by      VARCHAR(64)  NULL     DEFAULT '' COMMENT '创建者',
    create_time    DATETIME     NULL     DEFAULT NULL COMMENT '创建时间',
    update_by      VARCHAR(64)  NULL     DEFAULT '' COMMENT '更新者',
    update_time    DATETIME     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (notice_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知公告表';

-- -----------------------------------------------------------------------------
-- 后台操作日志表（物理删除）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_oper_log (
    oper_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    title          VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '操作模块',
    business_type  INT           NOT NULL DEFAULT 0 COMMENT '业务类型（0其他 1新增 2修改 3删除 4导出 5导入 6强退 7清空）',
    method         VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '请求方法',
    request_method VARCHAR(10)   NOT NULL DEFAULT '' COMMENT '请求方式',
    oper_name      VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '操作人员',
    dept_name      VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '部门名称',
    oper_url       VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '请求URL',
    oper_ip        VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '操作IP',
    oper_location  VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '操作地点',
    oper_param     VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '请求参数',
    json_result    VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '返回参数',
    status         INT           NOT NULL DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
    error_msg      VARCHAR(2000) NULL     DEFAULT '' COMMENT '异常消息',
    oper_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    cost_time      BIGINT        NOT NULL DEFAULT 0 COMMENT '消耗时间（毫秒）',
    PRIMARY KEY (oper_id),
    KEY idx_sys_oper_log_time (oper_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台操作日志表';

-- -----------------------------------------------------------------------------
-- 登录日志表（物理删除）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
    info_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    user_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '用户账号',
    ipaddr         VARCHAR(128) NOT NULL DEFAULT '' COMMENT '登录IP',
    login_location VARCHAR(255) NOT NULL DEFAULT '' COMMENT '登录地点',
    browser        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '浏览器类型',
    os             VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '操作系统',
    status         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
    msg            VARCHAR(255) NOT NULL DEFAULT '' COMMENT '提示消息',
    login_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (info_id),
    KEY idx_sys_login_log_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '登录日志表';

-- =============================================================================
-- 种子数据（全部 INSERT IGNORE + 固定主键：可重复执行，不覆盖已有修改）
-- =============================================================================

-- 部门树
INSERT IGNORE INTO sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, leader, status, create_by, create_time) VALUES
(1, 0,  '0',       '总公司',     0, '管理员', '0', 'admin', NOW()),
(2, 1,  '0,1',     '研发部门',   1, NULL,     '0', 'admin', NOW()),
(3, 1,  '0,1',     '市场部门',   2, NULL,     '0', 'admin', NOW()),
(4, 2,  '0,1,2',   '质量测试组', 1, NULL,     '0', 'admin', NOW());

-- 岗位
INSERT IGNORE INTO sys_post (post_id, post_code, post_name, post_sort, status, create_by, create_time) VALUES
(1, 'ceo',    '总经理',   1, '0', 'admin', NOW()),
(2, 'dev',    '研发工程师', 2, '0', 'admin', NOW()),
(3, 'tester', '测试工程师', 3, '0', 'admin', NOW()),
(4, 'market', '市场专员', 4, '0', 'admin', NOW());

-- 用户（密码为 BCrypt 哈希：admin/admin123、user/user123，仅本地首次登录使用）
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, email, phonenumber, sex, password, status, create_by, create_time) VALUES
(1, 2, 'admin', '管理员',   'admin@jiebaba.cn', '15888888888', '0', '$2a$10$Xp2VBMCAcYX.ssnhuWsfp.s1AoeOuX28FarAMzA9kxwH9xeVxSM/6', '0', 'admin', NOW()),
(2, 3, 'user',  '普通用户', 'user@jiebaba.cn',  '15666666666', '0', '$2a$10$ZpE7F1UWG89uVdA8kNtOEOZNE30PLK/DrkHHkyACLrVKy9PC1C1Fa', '0', 'admin', NOW());

-- 角色（data_scope：1全部 2自定义 3本部门 4本部门及以下 5仅本人）
INSERT IGNORE INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, create_by, create_time) VALUES
(1, '超级管理员', 'admin',  1, '1', '0', 'admin', NOW()),
(2, '普通角色',   'common', 2, '5', '0', 'admin', NOW());

-- 用户与角色/岗位关联
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2);
INSERT IGNORE INTO sys_user_post (user_id, post_id) VALUES (1, 1), (2, 4);

-- 菜单：覆盖设计已交付页面（系统管理目录 + 用户/角色/菜单/部门/岗位/字典/参数/公告 + 日志管理）
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
(1,   '系统管理', 0,   10, 'system', NULL,                'M', '0', '0', NULL,               'setting', 'admin', NOW()),
(100, '用户管理', 1,   1,  'user',   'system/user/index', 'C', '0', '0', 'system:user:list',   'user',    'admin', NOW()),
(101, '角色管理', 1,   2,  'role',   'system/role/index', 'C', '0', '0', 'system:role:list',   'peoples', 'admin', NOW()),
(102, '菜单管理', 1,   3,  'menu',   'system/menu/index', 'C', '0', '0', 'system:menu:list',   'tree',    'admin', NOW()),
(103, '部门管理', 1,   4,  'dept',   'system/dept/index', 'C', '0', '0', 'system:dept:list',   'tree',    'admin', NOW()),
(104, '岗位管理', 1,   5,  'post',   'system/post/index', 'C', '0', '0', 'system:post:list',   'post',    'admin', NOW()),
(105, '字典管理', 1,   6,  'dict',   'system/dict/index', 'C', '0', '0', 'system:dict:list',   'dict',    'admin', NOW()),
(106, '参数管理', 1,   7,  'config', 'system/config/index', 'C', '0', '0', 'system:config:list', 'edit',  'admin', NOW()),
(107, '通知公告', 1,   8,  'notice', 'system/notice/index', 'C', '0', '0', 'system:notice:list', 'message', 'admin', NOW()),
(108, '日志管理', 1,   9,  'log',    NULL,                'M', '0', '0', NULL,               'log',     'admin', NOW()),
(109, '操作日志', 108, 1,  'operlog',   'monitor/operlog/index',    'C', '0', '0', 'system:operlog:list',    'form', 'admin', NOW()),
(110, '登录日志', 108, 2,  'logininfor', 'monitor/logininfor/index', 'C', '0', '0', 'system:logininfor:list', 'key',  'admin', NOW());

-- 按钮权限
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
(1001, '用户查询', 100, 1, 'F', '0', '0', 'system:user:query',    '#', 'admin', NOW()),
(1002, '用户新增', 100, 2, 'F', '0', '0', 'system:user:add',      '#', 'admin', NOW()),
(1003, '用户修改', 100, 3, 'F', '0', '0', 'system:user:edit',     '#', 'admin', NOW()),
(1004, '用户删除', 100, 4, 'F', '0', '0', 'system:user:remove',   '#', 'admin', NOW()),
(1005, '用户导出', 100, 5, 'F', '0', '0', 'system:user:export',   '#', 'admin', NOW()),
(1006, '重置密码', 100, 6, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', NOW()),
(1007, '角色查询', 101, 1, 'F', '0', '0', 'system:role:query',    '#', 'admin', NOW()),
(1008, '角色新增', 101, 2, 'F', '0', '0', 'system:role:add',      '#', 'admin', NOW()),
(1009, '角色修改', 101, 3, 'F', '0', '0', 'system:role:edit',     '#', 'admin', NOW()),
(1010, '角色删除', 101, 4, 'F', '0', '0', 'system:role:remove',   '#', 'admin', NOW()),
(1011, '数据权限', 101, 5, 'F', '0', '0', 'system:role:dataScope', '#', 'admin', NOW()),
(1012, '菜单查询', 102, 1, 'F', '0', '0', 'system:menu:query',  '#', 'admin', NOW()),
(1013, '菜单新增', 102, 2, 'F', '0', '0', 'system:menu:add',    '#', 'admin', NOW()),
(1014, '菜单修改', 102, 3, 'F', '0', '0', 'system:menu:edit',   '#', 'admin', NOW()),
(1015, '菜单删除', 102, 4, 'F', '0', '0', 'system:menu:remove', '#', 'admin', NOW()),
(1016, '部门查询', 103, 1, 'F', '0', '0', 'system:dept:query',  '#', 'admin', NOW()),
(1017, '部门新增', 103, 2, 'F', '0', '0', 'system:dept:add',    '#', 'admin', NOW()),
(1018, '部门修改', 103, 3, 'F', '0', '0', 'system:dept:edit',   '#', 'admin', NOW()),
(1019, '部门删除', 103, 4, 'F', '0', '0', 'system:dept:remove', '#', 'admin', NOW()),
(1020, '岗位查询', 104, 1, 'F', '0', '0', 'system:post:query',  '#', 'admin', NOW()),
(1021, '岗位新增', 104, 2, 'F', '0', '0', 'system:post:add',    '#', 'admin', NOW()),
(1022, '岗位修改', 104, 3, 'F', '0', '0', 'system:post:edit',   '#', 'admin', NOW()),
(1023, '岗位删除', 104, 4, 'F', '0', '0', 'system:post:remove', '#', 'admin', NOW()),
(1024, '岗位导出', 104, 5, 'F', '0', '0', 'system:post:export', '#', 'admin', NOW()),
(1025, '字典查询', 105, 1, 'F', '0', '0', 'system:dict:query',  '#', 'admin', NOW()),
(1026, '字典新增', 105, 2, 'F', '0', '0', 'system:dict:add',    '#', 'admin', NOW()),
(1027, '字典修改', 105, 3, 'F', '0', '0', 'system:dict:edit',   '#', 'admin', NOW()),
(1028, '字典删除', 105, 4, 'F', '0', '0', 'system:dict:remove', '#', 'admin', NOW()),
(1029, '字典导出', 105, 5, 'F', '0', '0', 'system:dict:export', '#', 'admin', NOW()),
(1030, '参数查询', 106, 1, 'F', '0', '0', 'system:config:query',  '#', 'admin', NOW()),
(1031, '参数新增', 106, 2, 'F', '0', '0', 'system:config:add',    '#', 'admin', NOW()),
(1032, '参数修改', 106, 3, 'F', '0', '0', 'system:config:edit',   '#', 'admin', NOW()),
(1033, '参数删除', 106, 4, 'F', '0', '0', 'system:config:remove', '#', 'admin', NOW()),
(1034, '参数导出', 106, 5, 'F', '0', '0', 'system:config:export', '#', 'admin', NOW()),
(1035, '公告查询', 107, 1, 'F', '0', '0', 'system:notice:query',  '#', 'admin', NOW()),
(1036, '公告新增', 107, 2, 'F', '0', '0', 'system:notice:add',    '#', 'admin', NOW()),
(1037, '公告修改', 107, 3, 'F', '0', '0', 'system:notice:edit',   '#', 'admin', NOW()),
(1038, '公告删除', 107, 4, 'F', '0', '0', 'system:notice:remove', '#', 'admin', NOW()),
(1039, '操作查询', 109, 1, 'F', '0', '0', 'system:operlog:query',  '#', 'admin', NOW()),
(1040, '操作删除', 109, 2, 'F', '0', '0', 'system:operlog:remove', '#', 'admin', NOW()),
(1041, '操作导出', 109, 3, 'F', '0', '0', 'system:operlog:export', '#', 'admin', NOW()),
(1042, '操作清空', 109, 4, 'F', '0', '0', 'system:operlog:clean',  '#', 'admin', NOW()),
(1043, '登录查询', 110, 1, 'F', '0', '0', 'system:logininfor:query',  '#', 'admin', NOW()),
(1044, '登录删除', 110, 2, 'F', '0', '0', 'system:logininfor:remove', '#', 'admin', NOW()),
(1045, '登录导出', 110, 3, 'F', '0', '0', 'system:logininfor:export', '#', 'admin', NOW()),
(1046, '登录清空', 110, 4, 'F', '0', '0', 'system:logininfor:clean',  '#', 'admin', NOW());

-- 超级管理员角色授予全部菜单；普通角色仅可见系统管理目录
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 1, menu_id FROM sys_menu;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 1);

-- 常用字典
INSERT IGNORE INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time) VALUES
(1, '系统状态',   'sys_normal_disable', '0', 'admin', NOW()),
(2, '用户性别',   'sys_user_sex',       '0', 'admin', NOW()),
(3, '公告类型',   'sys_notice_type',    '0', 'admin', NOW()),
(4, '公告状态',   'sys_notice_status',  '0', 'admin', NOW()),
(5, '操作状态',   'sys_common_status',  '0', 'admin', NOW());

INSERT IGNORE INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time) VALUES
(1,  1, '正常', '0', 'sys_normal_disable', 'primary', 'Y', '0', 'admin', NOW()),
(2,  2, '停用', '1', 'sys_normal_disable', 'danger',  'N', '0', 'admin', NOW()),
(3,  1, '男',   '0', 'sys_user_sex',       'primary', 'Y', '0', 'admin', NOW()),
(4,  2, '女',   '1', 'sys_user_sex',       'danger',  'N', '0', 'admin', NOW()),
(5,  3, '未知', '2', 'sys_user_sex',       'info',    'N', '0', 'admin', NOW()),
(6,  1, '通知', '1', 'sys_notice_type',    'warning', 'Y', '0', 'admin', NOW()),
(7,  2, '公告', '2', 'sys_notice_type',    'success', 'N', '0', 'admin', NOW()),
(8,  1, '正常', '0', 'sys_notice_status',  'primary', 'Y', '0', 'admin', NOW()),
(9,  2, '关闭', '1', 'sys_notice_status',  'danger',  'N', '0', 'admin', NOW()),
(10, 1, '成功', '0', 'sys_common_status',  'primary', 'Y', '0', 'admin', NOW()),
(11, 2, '失败', '1', 'sys_common_status',  'danger',  'N', '0', 'admin', NOW());

-- 示例参数
INSERT IGNORE INTO sys_config (config_id, config_name, config_key, config_value, config_type, status, create_by, create_time) VALUES
(1, '登录验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', '0', 'admin', NOW()),
(2, '账号初始密码',   'sys.user.initPassword',      'admin123', 'Y', '0', 'admin', NOW()),
(3, '主框架页皮肤',   'sys.index.skinName',         'skin-blue', 'Y', '0', 'admin', NOW());

-- 示例公告
INSERT IGNORE INTO sys_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time) VALUES
(1, '欢迎使用 summer-admin', '1', '基于 summer-boot 4.2.0 的轻量级管理后台，默认账号 admin/admin123，仅用于本地首次登录。', '0', 'admin', NOW());
