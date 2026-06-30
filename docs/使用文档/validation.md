# 参数校验（summer-web）

手写 Bean Validation 最小子集，零第三方依赖。在控制器方法参数上标注 `@Valid` 触发递归校验，失败返回 `400` + 违规列表。

## 约束注解

| 注解 | 适用类型 | 说明 |
| --- | --- | --- |
| `@NotNull` | 任意 | 非 null |
| `@NotBlank` | 字符串 | 非 null 且去空白后非空 |
| `@NotEmpty` | 字符串/集合/数组 | 非 null 且长度/大小 > 0 |
| `@Min(value)` | 数字 | 不小于指定值 |
| `@Max(value)` | 数字 | 不大于指定值 |
| `@Size(min, max)` | 字符串/集合/数组 | 长度/大小在区间内 |
| `@Pattern(regexp)` | 字符串 | 匹配正则 |
| `@Email` | 字符串 | 合法邮箱格式 |

所有约束均支持 `message` 自定义提示（可用 `{value}`/`{min}`/`{max}`/`{regexp}` 占位符）。

## 触发校验

在控制器方法参数上同时标注 `@Valid` 与绑定注解：

```java
@PostMapping
@ResponseStatus(201)
public Product create(@Valid @RequestBody Product product) {
    productService.save(product);
    return product;
}
```

- `@Valid` 标在 `@RequestBody`/模型属性参数上时触发该校验；
- 校验**递归**进行：字段上若再标 `@Valid`，会继续校验嵌套对象；
- 仅 `@Valid` 参数会被校验；普通参数不校验。

## 实体示例

```java
@TableName("product")
public class Product implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @NotBlank(message = "name must not be blank")
    private String name;

    @Min(value = 0, message = "price must be non-negative")
    private Integer price;

    private Integer stock;
    // getters/setters...
}
```

## 失败响应

校验不通过时，`RequestDispatcher` 返回：

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "status": 400,
  "error": "Bad Request",
  "violations": [
    "name: name must not be blank",
    "price: price must be non-negative"
  ]
}
```

`violations` 数组每项为 `字段: 消息`。实测 `POST /products {"name":null,"price":-5}` 返回上述 400 与 2 条违规。

## 实现

`Validator` 递归扫描字段上的约束注解，违规收集为 `ConstraintViolation`；`HandlerMethodInvoker` 在绑定 `@Valid` 参数后调用校验，失败抛 `ValidationException`，由分发器转为 400 响应。