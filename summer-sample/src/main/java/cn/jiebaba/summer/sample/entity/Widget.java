package cn.jiebaba.summer.sample.entity;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableField;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;
import cn.jiebaba.summer.data.support.JsonTypeHandler;
import cn.jiebaba.summer.web.validation.NotBlank;

import java.util.Map;

/**
 * 演示通过 {@link JsonTypeHandler} 绑定的 JSONB 列。{@code attrs} 字段会被序列化为
 * JSON 文本，并由当前方言绑定到原生 JSON 列（PostgreSQL 用 jsonb、MySQL 用 json、
 * Oracle 用 CLOB），读取时透明还原。
 */
@TableName("summer_widget")
public class Widget {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @NotBlank(message = "name must not be blank")
    private String name;
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> attrs;

    public Widget() {}
    public Widget(String name, Map<String, Object> attrs) {
        this.name = name; this.attrs = attrs;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getAttrs() { return attrs; }
    public void setAttrs(Map<String, Object> attrs) { this.attrs = attrs; }
    @Override public String toString() {
        return "Widget{id=" + id + ", name=" + name + ", attrs=" + attrs + "}";
    }
}
