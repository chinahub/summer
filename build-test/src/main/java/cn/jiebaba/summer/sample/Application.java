package cn.jiebaba.summer.sample;

import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.boot.annotation.SummerBootApplication;

@SummerBootApplication
public class Application {
    public static void main(String[] args) {
        SummerApplication.run(Application.class, args);
    }
}