# 文件上传（multipart）

Summer 提供最小可用的 `multipart/form-data` 文件上传支持，纯 JDK 解析，零第三方依赖，参考 Spring Boot 的 `@RequestPart` / `MultipartFile` 设计。

## 设计要点

- **纯 JDK 解析**：`MultipartParser` 按 RFC 2388 解析 `multipart/form-data`，从已缓冲的请求体中拆分文件与表单字段。
- **Spring 风格抽象**：`MultipartFile`（`getName`/`getOriginalFilename`/`getContentType`/`getSize`/`getBytes`/`getInputStream`/`transferTo`）+ `@RequestPart`。
- **参数解析器**：`MultipartFileArgumentResolver` 实现 `HandlerMethodArgumentResolver`，由容器自动收集，支持注入 `MultipartFile`、`MultipartFile[]`、`List<MultipartFile>`，以及 `@RequestPart` 标注的表单字段。
- **自动装配**：`summer-boot` 的 `WebAutoConfiguration` 注册该解析器，引入 summer-boot 即生效，无需手动配置。

## 限制（最小可用）

- 请求体**整体先缓冲进内存**再解析（非流式），因此总上传大小受 `server.max-request-size` 约束（默认 10MB），超出会被 `RawHttpRequest` 直接拒绝（非 413）。
- 暂不支持 `Transfer-Encoding: chunked`（见 roadmap）。
- 单个文件大小受 `summer.web.multipart.max-file-size` 限制（默认 1MB），超出抛 400。

## 配置

```yaml
summer:
  web:
    multipart:
      max-file-size: 1MB      # 单个文件上限（字节数，支持 KB/MB/GB 后缀由 Environment 解析）
server:
  max-request-size: 10MB     # 整个请求体上限（含 multipart）
```

## 使用示例

```java
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.annotation.RequestPart;
import cn.jiebaba.summer.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class UploadController {

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file,
                                      @RequestPart(value = "description", required = false) String description)
            throws IOException {
        file.transferTo(new File("/tmp/" + file.getOriginalFilename()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", file.getOriginalFilename());
        result.put("size", file.getSize());
        result.put("description", description);
        return result;
    }
}
```

`curl` 测试：

```bash
curl -F "file=@hello.txt" -F "description=a text file" http://localhost:8080/upload
```

## 注意事项

- `getOriginalFilename()` 会剥离路径，只保留文件名，防止路径穿越写盘。
- 文件参数默认必填（`required=true`）；缺失抛 400。设 `@RequestPart(value="file", required=false)` 可允许缺省（注入 `null`）。
- 表单字段（Content-Disposition 中无 `filename` 的 part）通过 `@RequestPart` 以 `String` 注入。
- 大文件场景建议调大 `server.max-request-size`；真正流式/超大文件上传属于后续增强（配合 chunked 与流式解析）。