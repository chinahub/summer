package cn.jiebaba.summer.test.office;

import cn.jiebaba.summer.office.ocr.Ocr;
import cn.jiebaba.summer.office.ocr.OcrConfig;
import cn.jiebaba.summer.office.ocr.OcrItem;
import cn.jiebaba.summer.office.ocr.OcrResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR 冒烟测试：基于 PP-OCRv6 模型（识别模型内嵌字典）执行完整 检测->分类->识别 流水线。
 * <p>需外部资产：onnxruntime 原生库 + PP-OCRv6 检测/识别模型 + v4 分类模型。通过
 * {@code -Docr.assets=<目录>} 指定资产目录（默认 {@code C:/tmp/ocr-assets}），各资产路径亦可
 * 单独覆盖（{@code -Docr.lib} / {@code -Docr.det} / {@code -Docr.cls} / {@code -Docr.rec} / {@code -Docr.image}）。
 * <p>运行需启用原生访问：{@code java --enable-native-access=ALL-UNNAMED ...}。
 */
public class OcrSmokeTest {

    /**
     * 冒烟测试入口：加载 v6 模型并对测试图执行 OCR，打印识别结果与耗时。
     *
     * @param args 第一个参数可选，指定资产目录（等价于 -Docr.assets）
     */
    public static void main(String[] args) throws Exception {
        String base = System.getProperty("ocr.assets",
                args.length > 0 ? args[0] : "C:/tmp/ocr-assets");
        String lib = System.getProperty("ocr.lib",
                base + "/ort/onnxruntime-win-x64-1.20.1/lib/onnxruntime.dll");
        String det = System.getProperty("ocr.det", base + "/PP-OCRv6_det_small.onnx");
        String cls = System.getProperty("ocr.cls", base + "/ch_ppocr_mobile_v2.0_cls_infer.onnx");
        String rec = System.getProperty("ocr.rec", base + "/PP-OCRv6_rec_small.onnx");
        String img = System.getProperty("ocr.image", base + "/tool.png");

        require(lib, "onnxruntime 原生库");
        require(det, "PP-OCRv6 检测模型");
        require(rec, "PP-OCRv6 识别模型");

        OcrConfig config = OcrConfig.builder()
                .libPath(lib)
                .detModelPath(det)
                .clsModelPath(Files.exists(Path.of(cls)) ? cls : null)
                .recModelPath(rec)
                .build();

        byte[] image = Files.exists(Path.of(img))
                ? Files.readAllBytes(Path.of(img))
                : generateImage();

        long t0 = System.currentTimeMillis();
        try (Ocr ocr = Ocr.create(config)) {
            long initMs = System.currentTimeMillis() - t0;
            long t1 = System.currentTimeMillis();
            OcrResult result = ocr.recognize(image);
            long ocrMs = System.currentTimeMillis() - t1;
            System.out.println("init " + initMs + "ms, ocr " + ocrMs + "ms, blocks=" + result.size());
            System.out.println("----- TEXT -----");
            System.out.println(result.text());
            System.out.println("----- ITEMS -----");
            for (OcrItem it : result.items()) {
                System.out.printf("[%.3f] %s%n", it.score(), it.text());
            }
            if (result.size() == 0) {
                System.err.println("FAIL: 未识别到任何文本块");
                System.exit(1);
            }
            System.out.println("OK: PP-OCRv6 冒烟测试通过");
        }
    }

    /** 检查资产文件是否存在，不存在时打印提示并退出。 */
    private static void require(String path, String desc) {
        if (!Files.exists(Path.of(path))) {
            System.err.println("缺少" + desc + "：" + path);
            System.err.println("可通过 -Docr.assets=<资产目录> 指定，或单独覆盖 -Docr.lib/-Docr.det/-Docr.rec");
            System.exit(1);
        }
    }

    /** 无测试图时生成一张含英文文本的 PNG，用于验证流水线可用（中文识别请提供真实图片）。 */
    private static byte[] generateImage() throws Exception {
        System.out.println("（未找到测试图，生成简易英文图片）");
        BufferedImage bi = new BufferedImage(640, 120, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 640, 120);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        g.drawString("Summer OCR Test 2025", 20, 75);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", out);
        return out.toByteArray();
    }
}
