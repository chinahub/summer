package cn.jiebaba.summer.sample.entity;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableField;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;
import cn.jiebaba.summer.web.validation.Min;
import cn.jiebaba.summer.web.validation.NotBlank;

import java.io.Serializable;

@TableName("summer_product")
public class Product implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @NotBlank(message = "name must not be blank")
    private String name;
    @Min(value = 0, message = "price must be non-negative")
    private Integer price;
    @TableField("stock_qty")
    private Integer stock;
    @TableField(exist = false)
    private String transientFlag;

    public Product() {}
    public Product(String name, Integer price, Integer stock) {
        this.name = name; this.price = price; this.stock = stock;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    @Override public String toString() { return "Product{id=" + id + ", name=" + name + ", price=" + price + ", stock=" + stock + "}"; }
}
