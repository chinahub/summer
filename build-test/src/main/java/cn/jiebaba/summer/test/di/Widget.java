package cn.jiebaba.summer.test.di;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;

@TableName("widget")
public class Widget {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    public Widget() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
