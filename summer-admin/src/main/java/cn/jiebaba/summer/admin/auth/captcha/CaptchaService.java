package cn.jiebaba.summer.admin.auth.captcha;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.core.env.Environment;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;

/**
 * 一次性图形验证码服务：JDK {@link BufferedImage} 生成 PNG，
 * 内存保存 UUID → （SHA-256 不可逆摘要 + 过期时间），默认两分钟有效，
 * 验证即消费（成功失败都清除），大小写不敏感。验证码关闭时（测试/内部环境）
 * {@link #verify} 直接放行。进程内单实例，不伪装成集群方案。
 */
@Service
public class CaptchaService {

    /** 默认有效期：两分钟。 */
    public static final long DEFAULT_TTL_SECONDS = 120;

    /** 验证码字符集：去除易混淆字符（0/O、1/I 等）。 */
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final CaptchaStore store;
    private final boolean enabled;
    private final long ttlSeconds;
    private final CaptchaCodeGenerator codeGenerator;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public CaptchaService(Environment env) {
        this(new InMemoryCaptchaStore(),
                env.getProperty("summer.admin.captcha.enabled", Boolean.class, true),
                env.getProperty("summer.admin.captcha.ttl-seconds", Long.class, DEFAULT_TTL_SECONDS),
                null);
    }

    /** 完整构造器：测试注入确定性生成器与固定 TTL。 */
    public CaptchaService(CaptchaStore store, boolean enabled, long ttlSeconds,
                          CaptchaCodeGenerator codeGenerator) {
        this.store = store;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
        this.codeGenerator = codeGenerator == null ? this::randomCode : codeGenerator;
    }

    /** 当前存活验证码条数（测试与诊断用）。 */
    public int pendingCount() {
        return store.size();
    }

    /** 签发验证码：返回开关、UUID 与 Base64 PNG 图片；摘要只进内存，绝不外发。 */
    public CaptchaResult issue() {
        String code = codeGenerator.generate();
        String uuid = UUID.randomUUID().toString();
        if (enabled) {
            store.save(uuid, digest(uuid, code), Instant.now().plusSeconds(ttlSeconds));
            return new CaptchaResult(true, uuid, "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(render(code)));
        }
        return new CaptchaResult(false, uuid, "");
    }

    /**
     * 校验并消费验证码：开关关闭直接放行；UUID 或 code 空白、
     * 条目不存在/已过期、摘要不匹配均失败，且每次尝试都消费条目（一次性）。
     */
    public boolean verify(String uuid, String code) {
        if (!enabled) {
            return true;
        }
        if (uuid == null || uuid.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String expected = store.consume(uuid);
        if (expected == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                digest(uuid, code).getBytes(StandardCharsets.UTF_8));
    }

    /** 计算（UUID + 标准化验证码）的 SHA-256 十六进制摘要，不可逆。 */
    private static String digest(String uuid, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((uuid + ":" + code.toUpperCase(Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 生成 4 位随机验证码文本。 */
    private String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 渲染验证码图片：白底、抗锯齿、随机干扰线与噪点、4 位字符随机旋转。
     * 纯 JDK {@link BufferedImage} 实现，经 {@link ImageIO} 输出 PNG。
     *
     * @param code 验证码文本
     * @return PNG 字节
     */
    private byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180)));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            int step = WIDTH / (code.length() + 1);
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(20 + random.nextInt(120), 20 + random.nextInt(120), 20 + random.nextInt(120)));
                g.rotate(Math.toRadians(random.nextInt(50) - 25),
                        step * (i + 1), HEIGHT / 2.0);
                g.drawString(String.valueOf(code.charAt(i)), step * i + step / 2, 29);
                g.setTransform(g.getDeviceConfiguration().getDefaultTransform());
            }
            for (int i = 0; i < 40; i++) {
                g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("验证码图片编码失败", e);
        } finally {
            g.dispose();
        }
    }
}
