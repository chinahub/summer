# summer 4.0 全框架冒烟测试报告

> 测试日期：2026-09-23
> 被测版本：master `8526065`（summer-core 模块拆分后）+ 本次修复
> 环境：Windows 11 / JDK 25（D:\jdk\jdk-25.0.4）/ Maven 多模块 / WSL2 docker（pgvector17、oracle-free-23、mssql2022）
> 构建命令：`mvn -s settings.xml clean package`（JDK 25）

## 结论

**整体通过。** 8 模块全量构建成功；JUnit 单测 372 个（build-test 356 + sample 16）0 失败；16 个 main() 冒烟测试全部通过；`java -jar` 打包链路端到端验证通过。测试过程中发现并修复 6 处问题（见「修复清单」），全部回归通过。

## 一、JUnit 单元测试（surefire 自动执行）

| 模块 | Tests | 失败 | 跳过 | 说明 |
| --- | --- | --- | --- | --- |
| build-test | 356 | 0 | 1 | 跳过项为 StreamingToolLoopLlmTest（见「未覆盖项」） |
| summer-sample | 16 | 0 | 0 | office 风格对照 3 类（FastExcel/iText/POI） |

覆盖面：IoC 容器/循环依赖、AOP 双代理 + 自研字节码、条件注解、env/profile/YAML、自研 JSON、定时任务/Cron、SLF4J→JUL 绑定、Web 参数绑定/chunked/gzip/CORS/SSE/multipart、ORM wrapper/事务/类型处理器/方言转义、JWT/BCrypt/CSRF/多安全链、AI 协议/工具调用/记忆/RAG/弹性/A2A/JDBC 向量库与调用日志（真 PG）。

## 二、main() 冒烟测试（手工逐类运行）

| # | 测试类 | 覆盖内容 | 结果 |
| --- | --- | --- | --- |
| 1 | SmokeTest | sample 应用 11 条路由全动词 + 状态码断言 | 11 passed / 0 failed |
| 2 | KeepAliveSmokeTest | HTTP keep-alive 连接复用、close 语义 | 6 / 0 |
| 3 | TlsSmokeTest | 自签证书 TLS 握手 + keep-alive | 6 / 0 |
| 4 | WebSocketSmokeTest | 握手/@OnOpen/echo/ping-pong | 5 / 0 |
| 5 | SecuritySmokeTest | 登录/JWT/刷新/篡改/403/CSRF 等 26 场景 | 26 / 0 |
| 6 | MultiChainSmokeTest | 双 SecurityFilterChain 按 /api/** 分流 | 15 / 0 |
| 7 | OrmSmokeTest | SQL 生成/wrapper/分页/mapper 代理 | 32 / 0 |
| 8 | MultiDsSmokeTest | @DS 多数据源路由/嵌套恢复 | 8 / 0 |
| 9 | ExcelSmokeTest | xlsx 读写/样式/公式/合并/冻结 | 31 / 0 |
| 10 | DocxSmokeTest | docx 读写/表格/图片/样式 | 22 / 0 |
| 11 | MdHtmlSmokeTest | markdown/HTML 往返 | 23 / 0 |
| 12 | PdfSmokeTest | PDF 中文字体嵌入/往返 | 20 / 0 |
| 13 | OfficeFullTest | csv/xml/md/门面/流式 | 61 / 0 |
| 14 | OcrSmokeTest | PP-OCRv6 检测+方向分类+CRNN 识别全流水线（FFM 调 onnxruntime） | OK，中文识别正确 |
| 15 | DbSmokeTest | 真 PG：建表/CRUD/分页/事务提交+回滚（AOP 织入）/Bean 校验 | 16 / 0 |
| 16 | DialectDbSmokeTest | Oracle 13 断言 + SQL Server 13 断言：保留字转义/CLOB 回读/分页方言/keepalive 兜底 | 13+13 / 0 |

## 三、打包链路端到端（补齐此前零测试盲区）

- `summer-pack-maven-plugin` 产出 `summer-sample-4.0-boot.jar`（BOOT-INF 布局，1.86 MB）
- `java -jar` 经 JarLauncher 启动成功，**506ms** 就绪；命令行参数 `--server.port` / `--summer.ai.*` 覆盖生效
- HTTP 检查点：`GET /`、`GET /users`、`GET /users/1`、`GET /public/hello`（@PermitAll）、`POST /login`（JWT 签发）、`GET /me`（Bearer 鉴权 + authorities）全部 200
- `POST /products` 201（ASSIGN_ID 雪花 ID）、`GET /products?name=` 200（LambdaQueryWrapper + 真 PG）
- **AOP 切面实证**：修复切点后日志出现 `LoggingAspect - save completed on ...ProductServiceImpl.save` 与 `took 24ms`（修复前切点包名错误，切面完全不生效）
- 定时任务心跳（HeartbeatTask）按 fixedDelay 触发正常

## 四、修复清单（本次随测试提交）

| # | 问题 | 修复 | 类型 |
| --- | --- | --- | --- |
| 1 | sample `LoggingAspect` 切点写成 `io.summer.sample.repository..*`（实际包为 `cn.jiebaba.summer...`），切面永远不匹配，AOP 在示例应用中完全失效 | 改为正确包名，并以打包链路日志实证生效 | 示例代码 bug |
| 2 | `SmokeTest` 只打印响应不断言，失败无法客观判定 | 补 11 条状态码断言 + 失败非零退出 | 测试代码缺陷 |
| 3 | `KeepAliveSmokeTest`/`WebSocketSmokeTest` 固定占用 8080 端口 | 改 `server.port=0` 随机端口 | 测试代码缺陷 |
| 4 | `KeepAliveSmokeTest`/`TlsSmokeTest`/`WebSocketSmokeTest`/`MultiChainSmokeTest` 未设 `summer.ai.*` 占位属性，summer-ai 在 classpath 上自动装配快速失败，**四个测试启动即挂**；且异常被吞、退出码 0（假绿） | 与 SmokeTest 对齐：补占位属性 + 失败 `System.exit(1)` | 测试代码缺陷（回归自 AI 自动配置引入） |
| 5 | `expect()` 失败不影响退出码 | KeepAlive/Tls/WebSocket 补 failed 计数与非零退出 | 测试代码缺陷 |
| 6 | 源码树混入 2 个未跟踪 `.class` 编译产物（summer-core/env、summer-boot/web/http） | 已删除 | 工作区卫生 |

> 测试环境备注：sample 无自动建表，冒烟前需在 PG 预建 `summer_product(id,name,price,stock_qty)` 与 `summer_widget(id,name,attrs JSONB)`；WSL VM 空闲会自动关停（连带 docker 容器），长时间操作需保持 WSL 会话存活。

## 五、未覆盖项与已知盲区

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 真实 LLM 流式工具调用（StreamingToolLoopLlmTest） | SKIP | 依赖 PG `ai_provider_info` 表中的有效 api-key，当前库中无此表，按设计 assumeTrue 跳过；其余 AI 功能均以 Stub 全覆盖 |
| `ApplicationReadyEvent`/`ApplicationFailedEvent` | 无测试 | boot.event 两事件全仓库零引用；本次启动日志可间接佐证 Ready 路径 |
| `OnnxEngine` 直测 | 无直接单测 | 仅经 OCR 冒烟间接覆盖（真模型推理通过） |
| `DailyRollingFileHandler` 滚动 | 无专门测试 | 仅 Slf4jBindingTest 间接覆盖日志初始化 |
| `web.validation` 独立单测 | 无 | 约束校验经 sample `/products` @Valid 与 DbSmokeTest 覆盖 |
| 静态资源服务、idle 超时 | 无测试 | 冒烟未触及 |
| summer-boot-loader/pack 插件单测 | 无 | 本次以 `java -jar` 端到端覆盖主路径，插件内部逻辑仍无单测 |

## 六、复现方式

```bash
export JAVA_HOME="D:\jdk\jdk-25.0.4" && export PATH="/d/jdk/jdk-25.0.4/bin:$PATH"

# 全量构建 + 单测
mvn -s settings.xml clean package

# main() 冒烟（先导出 classpath）
mvn -s settings.xml -pl build-test dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
mvn -s settings.xml -pl build-test test-compile -q
CP="$(cat /tmp/cp.txt);build-test/target/classes"
java -Dfile.encoding=UTF-8 -cp "$CP" cn.jiebaba.summer.test.SmokeTest
java -Dfile.encoding=UTF-8 -cp "$CP" cn.jiebaba.summer.test.DbSmokeTest                      # 需 PG
java -Dfile.encoding=UTF-8 -cp "$CP" cn.jiebaba.summer.test.DialectDbSmokeTest oracle        # 需 Oracle
java -Dfile.encoding=UTF-8 -cp "$CP" cn.jiebaba.summer.test.DialectDbSmokeTest sqlserver     # 需 SQL Server
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -cp "$CP" cn.jiebaba.summer.test.office.OcrSmokeTest  # 需 ONNX 资产(C:/tmp/ocr-assets)

# 打包链路
java -Dfile.encoding=UTF-8 -jar summer-sample/target/summer-sample-4.0-boot.jar \
  --server.port=18080 --summer.ai.provider=deepseek --summer.ai.api-key=dummy-not-used
```
