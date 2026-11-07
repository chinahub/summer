package cn.jiebaba.summer.admin.system.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态路由视图对象：前端路由表节点。目录与菜单进入路由树，按钮只作为权限
 * 标识参与鉴权，不出现为路由节点。根节点 component 固定为 Layout，
 * 子节点 component 取 sys_menu.component。
 */
public class RouterVo {

    /** 路由名称（path 首字母大写） */
    private String name;

    /** 路由地址（根节点带 / 前缀） */
    private String path;

    /** 是否隐藏（sys_menu.visible = 1） */
    private Boolean hidden;

    /** 重定向地址（目录默认 redirect: noRedirect） */
    private String redirect;

    /** 组件路径（根节点为 Layout） */
    private String component;

    /** 路由参数 */
    private String query;

    /** 元数据：标题、图标与缓存策略 */
    private Meta meta;

    /** 子路由 */
    private List<RouterVo> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public List<RouterVo> getChildren() {
        return children;
    }

    public void setChildren(List<RouterVo> children) {
        this.children = children;
    }

    /** 路由元数据。 */
    public static class Meta {

        /** 标题（菜单名称） */
        private String title;

        /** 图标 */
        private String icon;

        /** 是否不缓存（sys_menu.is_cache = 1） */
        private boolean noCache;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public boolean isNoCache() {
            return noCache;
        }

        public void setNoCache(boolean noCache) {
            this.noCache = noCache;
        }
    }
}
