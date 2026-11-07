package cn.jiebaba.summer.admin;

import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.boot.annotation.SummerBootApplication;

/**
 * summer-admin 应用入口：扫描 {@code cn.jiebaba.summer.admin} 包并启动嵌入式 Web 服务。
 */
@SummerBootApplication
public class SummerAdminApplication {

    public static void main(String[] args) {
        SummerApplication.run(SummerAdminApplication.class, args);
    }
}
