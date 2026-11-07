package cn.jiebaba.summer.admin.common.web;

import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.Map;

/**
 * 健康检查端点：返回 R 包装的 {@code {status: UP}}。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of("status", "UP"));
    }
}
