# 安装

## 环境要求

- **JDK 25+**（最低要求 JDK 25，实测用 JDK 25.0.2 LTS）
- **Maven**（pom modelVersion 4.0.0；实测用 mvnd 1.0.5 内置 Maven 3.9.14，离线构建）

## 本机环境实测路径

| 工具 | 路径 | 说明 |
| --- | --- | --- |
| JDK 25 | `D:\jdk\jdk-25.0.2` | `bin\java.exe` |
| mvnd | `D:\mvnd-1.0.5` | 内置标准 Maven 在 `mvn\bin\mvn.cmd` |
| 工作区 | `E:\summer_workspace` | 可写 |

## 构建准备（离线）

### 1. 指定 JDK 25

```powershell
$env:JAVA_HOME='D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;D:\mvnd-1.0.5\mvn\bin;" + $env:Path
```

### 2. 离线 settings.xml

本地仓库构件被标记为仓库 id `public`，离线模式下需用同名镜像匹配，否则被判为「未下载」。
项目根目录提供 `settings.xml`：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors>
    <mirror>
      <id>public</id>
      <name>local cache mirror</name>
      <url>https://repo.maven.apache.org/maven2</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

> 注意：mvnd 的守护进程在沙箱里无法建立 loopback 连接，**不要用 `mvnd.exe`**，改用其内置的标准 `mvn.cmd`。

### 3. 构建命令

```powershell
# 全量离线构建（4 模块全部 SUCCESS）
mvn -s E:\summer_workspace\settings.xml -o clean package
```

## 编码注意事项

- **源文件必须无 BOM**：JDK 编译器遇到 UTF-8 BOM 会报「非法字符: \ufeff」。
- 用 PowerShell 写 Java 文件时，统一用 `.NET` 的 `UTF8Encoding(false)`（无 BOM）写入。
- 编译器开启 `-parameters`（父 POM 已配置 `<parameters>true</parameters>`），用于按参数名做依赖注入与查询参数绑定。

## JPMS 反射要求（重要）

框架用反射做 IoC 注入、参数绑定与 JSON 序列化。业务模块必须把含 bean/模型/控制器的包 `opens` 给框架，否则会抛 `InaccessibleObjectException`：

```java
module summer.sample {
    requires summer.boot;
    requires summer.web;
    requires summer.core;

    opens cn.jiebaba.summer.sample to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.controller to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.service to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.model to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.config to summer.core, summer.web;
    opens cn.jiebaba.summer.sample.advice to summer.core, summer.web;
}
```

> 这是 JPMS 的强封装契约（类似 Spring 要求 `opens ... to spring.core`）。`opens` 不递归，需逐包声明。

## 验证

```powershell
# 全量构建
mvn -s E:\summer_workspace\settings.xml -o clean package
# 启动示例（模块化）
java -p summer-core/target/summer-core-1.0.0-SNAPSHOT.jar;summer-web/target/summer-web-1.0.0-SNAPSHOT.jar;summer-boot/target/summer-boot-1.0.0-SNAPSHOT.jar;summer-sample/target/summer-sample-1.0.0-SNAPSHOT.jar -m summer.sample/cn.jiebaba.summer.sample.Application
# 进程内冒烟测试（同进程 loopback，规避沙箱进程间网络限制）
java -p <同上> -m summer.sample/cn.jiebaba.summer.sample.SmokeTest
```

实测：4 模块 BUILD SUCCESS；服务器 `started on 0.0.0.0:8080 (virtual threads, 8 routes)`；SmokeTest 全部路由返回正确状态码与 JSON。

