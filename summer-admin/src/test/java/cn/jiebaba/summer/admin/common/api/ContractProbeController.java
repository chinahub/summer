package cn.jiebaba.summer.admin.common.api;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.validation.RequestValidator;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.validation.ConstraintViolation;
import cn.jiebaba.summer.web.validation.NotBlank;
import cn.jiebaba.summer.web.validation.ValidationException;

import java.util.List;

/**
 * 契约探针控制器（仅测试源码）：为 {@link ApiContractTest} 提供触发
 * 各类错误路径的最小端点，不进入生产路由。
 */
@RestController
public class ContractProbeController {

    /** 显式校验用的请求表单：name 不允许空白。 */
    public static class ProbeForm {
        @NotBlank(message = "名称不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @GetMapping("/api/probe/business")
    public String business() {
        throw new BusinessException(409, "演示业务冲突");
    }

    @PostMapping("/api/probe/validated")
    public String validated(@RequestBody ProbeForm form) {
        // 统一契约要求：控制器内显式校验，而非参数级 @Valid
        RequestValidator.requireValid(form);
        return form.getName();
    }

    @PostMapping("/api/probe/framework-validation")
    public String frameworkValidation(@RequestBody ProbeForm form) {
        // 模拟框架注解校验失败时的内置错误格式（violations 体）
        throw new ValidationException(List.of(
                new ConstraintViolation("name", form == null ? null : form.getName(), "名称不能为空")));
    }

    @GetMapping("/api/probe/boom")
    public String boom() {
        // 异常消息携带 SQL 与敏感串：500 响应必须只回安全通用消息
        throw new RuntimeException("SELECT password FROM sys_user WHERE token='probe-boom-secret'");
    }

}
