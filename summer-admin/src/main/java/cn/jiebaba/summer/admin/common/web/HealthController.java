package cn.jiebaba.summer.admin.common.web;

import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.Map;

/**
 * 健康检查端点：当前直接返回 Map，Task 4 将统一改为 {@code R.ok(...)} 包装。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
