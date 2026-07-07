package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestHeader;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

/** 供 HandlerMethodInvoker 测试用的控制器：覆盖路径变量、请求体、请求参数、请求头等绑定情形。 */
@RestController
public class SampleController {

    public String get(@PathVariable Long id) {
        return "id=" + id;
    }

    public String create(@RequestBody User user) {
        return "name=" + user.name() + ",age=" + user.age();
    }

    public String update(@PathVariable Long id, @RequestBody User user) {
        return "id=" + id + ":" + user.name();
    }

    public String search(@RequestParam("name") String name,
                         @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return name + ":" + limit;
    }

    public String header(@RequestHeader("X-Trace") String trace) {
        return "trace=" + trace;
    }

    public String noargs() {
        return "ok";
    }

    public void fire() {
        // void 返回：调用器应返回 null
    }

    public int count() {
        return 7;
    }

    public String echo(@RequestParam("n") int n) {
        return "n=" + n;
    }

    public User recordReturn() {
        return new User(7L, "x", 9);
    }

    public String boom() throws Exception {
        throw new Exception("boom");
    }
}
