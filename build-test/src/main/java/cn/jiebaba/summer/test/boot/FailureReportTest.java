package cn.jiebaba.summer.test.boot;

import cn.jiebaba.summer.boot.SummerApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** 启动失败诊断报告（APPLICATION FAILED TO START）的生成测试。 */
public class FailureReportTest {

    @Test
    public void reportContainsStageAndError() {
        IllegalStateException e = new IllegalStateException(
                "summer-ai 已在 classpath 但未正确配置：请设置 summer.ai.provider");
        String report = SummerApplication.buildFailureReport(0, "container", e);
        Assertions.assertTrue(report.contains("APPLICATION FAILED TO START"), report);
        Assertions.assertTrue(report.contains("Stage: container"), report);
        Assertions.assertTrue(report.contains("IllegalStateException"), report);
        Assertions.assertTrue(report.contains("summer.ai.provider"), report);
        Assertions.assertTrue(report.contains("Action:"), report);
    }

    @Test
    public void reportExtractsRootCause() {
        IllegalStateException e = new IllegalStateException("outer",
                new IllegalArgumentException("root message"));
        String report = SummerApplication.buildFailureReport(0, "web", e);
        Assertions.assertTrue(report.contains("Root cause:"), report);
        Assertions.assertTrue(report.contains("IllegalArgumentException: root message"), report);
    }
}
