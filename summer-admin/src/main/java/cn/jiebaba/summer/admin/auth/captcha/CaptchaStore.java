package cn.jiebaba.summer.admin.auth.captcha;

import java.time.Instant;

/**
 * 验证码存储：保存 UUID 到不可逆摘要的映射，验证即消费。
 * 内存单实例实现见 {@link InMemoryCaptchaStore}，不伪装成集群方案。
 */
public interface CaptchaStore {

    /** 保存验证码摘要与过期时间；同 UUID 覆盖旧值。 */
    void save(String uuid, String digest, Instant expiresAt);

    /**
     * 原子取回并消费：存在且未过期返回摘要；不存在或已过期返回 null。
     * 无论成功与否条目都被移除（一次性语义）。
     */
    String consume(String uuid);

    /** 当前存活的验证码条数（用于测试与诊断）。 */
    int size();
}
