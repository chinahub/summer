package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.core.annotation.Service;

import java.util.List;
import java.util.Map;

/**
 * 操作日志切面的被代理夹具：以真实 IoC 容器 + 子类代理路径验证
 * {@link OperLog} 的拦截、脱敏、异常与失败隔离语义。
 */
@Service
public class OperLogFixtureService {

    /** 模拟带密码的新增写操作。 */
    @OperLog(module = "用户管理", action = "新增")
    public String createUser(String username, String password) {
        return "created:" + username;
    }

    /** 模拟抛业务异常的删除写操作。 */
    @OperLog(module = "用户管理", action = "删除")
    public String deleteUser(String username) {
        throw new BusinessException(409, "禁止删除演示用户");
    }

    /** 模拟携带嵌套敏感字段的修改写操作。 */
    @OperLog(module = "参数管理", action = "修改")
    public String updateConfig(Map<String, Object> body) {
        return "updated";
    }

    /** 未加注解的普通方法：不应产生任何日志。 */
    public String ping() {
        return "pong";
    }

    /** 携带认证主体与原始字节数组的写操作：两者都不应进入操作参数。 */
    @OperLog(module = "用户管理", action = "导入")
    public String importUsers(List<Object> mixed) {
        return "imported";
    }
}
