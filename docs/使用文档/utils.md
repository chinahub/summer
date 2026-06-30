# summer 工具集（utils）

> summer-core 内置工具类集合，全部基于 JDK 实现，零第三方依赖。

summer 在 `cn.jiebaba.summer.core.util` 包下提供以下工具类，API 风格参考 commons-lang3 与 hutool，业务代码可直接静态调用。

| 工具类 | 参考库 | 说明 |
| --- | --- | --- |
| `StringUtil` | commons-lang3 StringUtils | 判空、截取、拼接、填充、替换、大小写、判断等 |
| `DateUtil` | hutool DateUtil | 格式化/解析、偏移、区间、边界、字段提取（基于 java.time） |
| `JsonUtil` | hutool JSONUtil | 序列化/解析/类型绑定，含 JSONObject / JSONArray |
| `SecurityUtil` | hutool SecureUtil | 摘要、HMAC、AES/DES/RSA 加解密、签名、Base64/Hex、UUID |
| `SummerUtil` | — | IoC 容器静态门面：获取 / 注册 / 注销 Bean |

## StringUtil

参考 `org.apache.commons.lang3.StringUtils`，所有方法对 `null` 容错。

```java
StringUtil.isEmpty("");          // true
StringUtil.isBlank("   ");       // true
StringUtil.split("a,b,c", ",");  // ["a","b","c"]
StringUtil.join(list, "-");      // 拼接
StringUtil.capitalize("hello");  // Hello
StringUtil.leftPad("1", 3, '0'); // 001
StringUtil.substringBeforeLast("a.b.c", "."); // a.b
StringUtil.equalsAny("b", "a","b","c");        // true
```

## DateUtil

参考 `cn.hutool.core.date.DateUtil`，底层使用 `java.time`，并提供 `java.util.Date` 互转。

```java
DateUtil.now();                                   // 2024-06-15 10:20:30
DateUtil.formatDate(new Date());                  // yyyy-MM-dd
DateUtil.parseDateTime("2024-06-15 10:20:30");    // Date
DateUtil.offsetDay(date, 1);                      // 加一天
DateUtil.betweenDay(begin, end);                  // 相差天数
DateUtil.beginOfMonth(date);                      // 月初 00:00:00
DateUtil.endOfMonth(date);                        // 月末 23:59:59
DateUtil.dayOfWeek(date);                         // ISO 1=周一 ... 7=周日
DateUtil.isWeekend(date);                         // 是否周末
```

`offset(Date, DateField, amount)` 与 `between(Date, Date, DateUnit)` 通过枚举指定字段/单位。

## JsonUtil

参考 `cn.hutool.json.JSONUtil`，纯 JDK 实现的序列化与解析，支持 record、JavaBean、Map、集合、数组、枚举、`Optional`、`java.time`、`Date`。

```java
JsonUtil.toJsonStr(obj);               // 序列化为紧凑 JSON
JsonUtil.toJsonPrettyStr(obj);         // 带缩进
JsonUtil.parseObj("{\"a\":1}");        // JSONObject
JsonUtil.parseArray("[1,2,3]");        // JSONArray
JsonUtil.toBean(json, User.class);     // 反序列化为对象
JsonUtil.toList(json, User.class);     // 反序列化为 List
JsonUtil.isJson(text);                 // 是否合法 JSON

JsonUtil.JSONObject obj = JsonUtil.parseObj(json);
obj.getStr("name"); obj.getInt("age"); obj.getJSONObject("nested");
```

## SecurityUtil

参考 `cn.hutool.crypto.SecureUtil`，基于 `java.security` / `javax.crypto`。

```java
SecurityUtil.md5Hex("abc");                 // 900150983cd24fb0d6963f7d28e17f72
SecurityUtil.sha256Hex("abc");
SecurityUtil.hmacSha256Hex("data", "secret");

// 对称加密：String 重载自动派生密钥并输出 Base64
String enc = SecurityUtil.encryptAES("明文", "key");
SecurityUtil.decryptAES(enc, "key");        // 明文

// 非对称加密 + 签名
KeyPair kp = SecurityUtil.generateRSAKeyPair();
SecurityUtil.encryptRSABase64("data", kp.getPublic());
SecurityUtil.signBase64(kp.getPrivate(), "msg");
SecurityUtil.verifyBase64(kp.getPublic(), "msg", sig);

SecurityUtil.encodeBase64(data); SecurityUtil.encodeHex(data);
SecurityUtil.randomUUID(); SecurityUtil.simpleUUID();
```

## SummerUtil

IoC 容器的静态门面，提供 Bean 的获取、注册与注销。上下文由 `SummerApplication.run()` 自动绑定。

```java
SummerUtil.getBean(MyService.class);
SummerUtil.getBean("myService");
SummerUtil.containsBean("myService");

// 注册：以实例直接注册为单例
SummerUtil.registerBean(new MyService());            // 名称为 decapitalize(类名)
SummerUtil.registerBean("custom", new MyService());  // 指定名称

// 注销：返回是否移除成功，并触发 PreDestroy / DisposableBean 回调
SummerUtil.unregisterBean("custom");
SummerUtil.unregisterBean(MyService.class);          // 按类型注销全部
```

> 注意：`registerBean` 在同名 Bean 已存在时会抛出 `BeansException`，需先 `unregisterBean` 再注册以替换。