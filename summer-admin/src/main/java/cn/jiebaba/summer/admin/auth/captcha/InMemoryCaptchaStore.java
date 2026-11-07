package cn.jiebaba.summer.admin.auth.captcha;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内单实例验证码存储：{@link ConcurrentHashMap} 保存 UUID 到
 * （不可逆摘要 + 过期时间）的映射，{@code consume} 用 {@link Map#remove}
 * 保证原子消费；{@code save} 时惰性清理过期项，防止长期运行内存膨胀。
 * 仅支持单实例部署，不伪装成集群方案。
 */
public class InMemoryCaptchaStore implements CaptchaStore {

    /** 验证码条目：摘要与绝对过期时间。 */
    private record Entry(String digest, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(String uuid, String digest, Instant expiresAt) {
        evictExpired(Instant.now());
        entries.put(uuid, new Entry(digest, expiresAt));
    }

    @Override
    public String consume(String uuid) {
        Entry entry = entries.remove(uuid);
        if (entry == null) {
            return null;
        }
        if (!entry.expiresAt().isAfter(Instant.now())) {
            return null;
        }
        return entry.digest();
    }

    @Override
    public int size() {
        return entries.size();
    }

    /** 惰性清理所有已过期条目，仅由 {@code save} 触发，避免引入定时线程。 */
    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (!e.getValue().expiresAt().isAfter(now)) {
                it.remove();
            }
        }
    }
}
