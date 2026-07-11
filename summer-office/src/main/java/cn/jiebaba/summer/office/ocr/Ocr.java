package cn.jiebaba.summer.office.ocr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR 门面：基于 ONNX Runtime（FFM）加载 PP-OCR 检测/分类/识别模型，对图像执行完整 OCR 流水线。
 * <p>流水线（移植自 RapidAI/rapidocr）：
 * <ol>
 *   <li>预处理：将图像缩放到 [minSideLen, maxSideLen] 边界内（32 倍数）；</li>
 *   <li>纵向补边：过扁的图像上下补黑，避免检测漏行；</li>
 *   <li>检测：DB 模型输出文本框；</li>
 *   <li>裁剪：透视矫正四边形为矩形；</li>
 *   <li>分类：判断 0/180 度并旋转；</li>
 *   <li>识别：CRNN+CTC 解码为文本；</li>
 *   <li>按置信度过滤，框坐标回映到原图。</li>
 * </ol>
 * <p>用法：{@code Ocr ocr = Ocr.create(config); OcrResult r = ocr.recognize(imageBytes);}
 */
public final class Ocr implements AutoCloseable {

    private final OnnxEngine engine;
    private final OnnxEngine.Model detModel;
    private final OnnxEngine.Model clsModel;
    private final OnnxEngine.Model recModel;
    private final TextDetector detector;
    private final TextClassifier classifier;
    private final TextRecognizer recognizer;
    private final OcrConfig config;

    /** 加载 onnxruntime 引擎与检测/分类/识别模型，构建三阶段处理器与字典。 */
    private Ocr(OcrConfig config) {
        this.config = config;
        this.engine = OnnxEngine.load(config.libPath());
        this.detModel = engine.loadModel(readBytes(config.detModelPath()));
        this.clsModel = config.clsModelPath() != null && !config.clsModelPath().isBlank()
                ? engine.loadModel(readBytes(config.clsModelPath())) : null;
        this.recModel = engine.loadModel(readBytes(config.recModelPath()));
        this.detector = config.useDet() ? new TextDetector(detModel, config) : null;
        this.classifier = (config.useCls() && clsModel != null) ? new TextClassifier(clsModel, config) : null;
        this.recognizer = config.useRec() ? new TextRecognizer(recModel, config, resolveDict(recModel, config.dictPath())) : null;
    }

    /** 按配置创建 OCR 引擎（加载原生库与模型）。 */
    public static Ocr create(OcrConfig config) {
        return new Ocr(config);
    }

    /**
     * 识别图像中的文本。
     *
     * @param imageBytes 图片字节（PNG/JPEG/BMP 等）
     * @return 识别结果
     */
    public OcrResult recognize(byte[] imageBytes) {
        ImageUtil.Img ori = ImageUtil.decode(imageBytes);
        ImageUtil.Img resized = resizeWithinBounds(ori);
        float ratioW = (float) ori.width / resized.width;
        float ratioH = (float) ori.height / resized.height;
        int padTop;
        ImageUtil.Img padded;
        if (resized.height <= config.minHeight() || (float) resized.width / resized.height > config.widthHeightRatio()) {
            int padH = Math.max((int) (resized.width / config.widthHeightRatio()), config.minHeight());
            padH = Math.abs(padH * 2 - resized.height) / 2;
            padded = ImageUtil.pad(resized, padH, padH, 0, 0, 0);
            padTop = padH;
        } else {
            padded = resized;
            padTop = 0;
        }

        List<DbPostProcess.DetBox> boxes;
        List<ImageUtil.Img> crops;
        if (detector != null) {
            boxes = detector.detect(padded);
            if (boxes.isEmpty()) {
                return new OcrResult(List.of());
            }
            crops = new ArrayList<>(boxes.size());
            for (DbPostProcess.DetBox db : boxes) {
                crops.add(cropRegion(padded, db.box));
            }
        } else {
            boxes = List.of();
            crops = List.of(padded);
        }

        if (classifier != null) {
            crops = classifier.classify(crops);
        }

        List<OcrItem> items = new ArrayList<>();
        if (recognizer != null) {
            List<TextRecognizer.Rec> recs = recognizer.recognize(crops);
            int count = Math.min(boxes.size(), recs.size());
            for (int i = 0; i < count; i++) {
                TextRecognizer.Rec rec = recs.get(i);
                if (rec.text.isBlank() || rec.score < config.textScore()) {
                    continue;
                }
                float[][] box = boxes.isEmpty() ? null : mapToOriginal(boxes.get(i).box, padTop, ratioW, ratioH,
                        ori.width, ori.height);
                items.add(new OcrItem(rec.text, box, rec.score));
            }
        } else {
            for (DbPostProcess.DetBox db : boxes) {
                float[][] box = mapToOriginal(db.box, padTop, ratioW, ratioH, ori.width, ori.height);
                items.add(new OcrItem("", box, db.score));
            }
        }
        return new OcrResult(items);
    }

    /** 透视矫正裁剪文本区域；高宽比 >=1.5 时旋转 90 度。 */
    private ImageUtil.Img cropRegion(ImageUtil.Img src, float[][] box) {
        int cropW = (int) Math.max(dist(box[0], box[1]), dist(box[2], box[3]));
        int cropH = (int) Math.max(dist(box[0], box[3]), dist(box[1], box[2]));
        if (cropW < 1) {
            cropW = 1;
        }
        if (cropH < 1) {
            cropH = 1;
        }
        ImageUtil.Img crop = ImageUtil.warpPerspective(src, box, cropW, cropH);
        if (cropH * 1.0 / cropW >= 1.5) {
            crop = ImageUtil.rotate90(crop);
        }
        return crop;
    }

    /** 将检测框从预处理图坐标回映到原图坐标：先减补边、再乘缩放比、最后裁剪到边界。 */
    private float[][] mapToOriginal(float[][] box, int padTop, float ratioW, float ratioH, int oriW, int oriH) {
        float[][] out = new float[4][2];
        for (int i = 0; i < 4; i++) {
            float x = box[i][0] * ratioW;
            float y = (box[i][1] - padTop) * ratioH;
            out[i][0] = clampF(x, 0, oriW);
            out[i][1] = clampF(y, 0, oriH);
        }
        return out;
    }

    /** 缩放到 [minSideLen, maxSideLen] 边界内，长宽均取 32 倍数。 */
    private ImageUtil.Img resizeWithinBounds(ImageUtil.Img img) {
        int h = img.height;
        int w = img.width;
        ImageUtil.Img cur = img;
        if (Math.max(h, w) > config.maxSideLen()) {
            float r = (float) config.maxSideLen() / Math.max(h, w);
            cur = ImageUtil.resize(img, round32(w * r), round32(h * r));
        }
        h = cur.height;
        w = cur.width;
        if (Math.min(h, w) < config.minSideLen()) {
            float r = (float) config.minSideLen() / Math.min(h, w);
            cur = ImageUtil.resize(cur, round32(w * r), round32(h * r));
        }
        return cur;
    }

    private static int round32(float v) {
        int r = Math.round(v / 32f) * 32;
        return r < 32 ? 32 : r;
    }

    private static float dist(float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static byte[] readBytes(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new OcrException("读取文件失败：" + path, e);
        }
    }

    /**
     * 解析识别字典：优先使用模型内嵌的 character 元数据（PP-OCRv4/v6 均支持），
     * 若模型未内嵌字典则回退到外部字典文件；两者均不可用时抛出异常。
     *
     * @param recModel 识别模型会话
     * @param dictPath 外部字典路径，可为 null（模型内嵌字典时无需提供）
     * @return 字符列表，每项一个字符
     */
    private static List<String> resolveDict(OnnxEngine.Model recModel, String dictPath) {
        String embedded = recModel.getCustomMetadata("character");
        if (embedded != null && !embedded.isBlank()) {
            List<String> chars = new ArrayList<>();
            for (String line : embedded.lines().toList()) {
                if (!line.isEmpty()) {
                    chars.add(line);
                }
            }
            return chars;
        }
        if (dictPath == null || dictPath.isBlank()) {
            throw new OcrException("识别模型未内嵌字典且未配置 dictPath");
        }
        return readDict(dictPath);
    }

    /** 读取字典文件，每行一个字符（UTF-8）。 */
    private static List<String> readDict(String path) {
        try {
            List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
            List<String> chars = new ArrayList<>(lines.size());
            for (String line : lines) {
                String s = line.replace("\r", "");
                if (!s.isEmpty()) {
                    chars.add(s);
                }
            }
            return chars;
        } catch (IOException e) {
            throw new OcrException("读取字典失败：" + path, e);
        }
    }

    @Override
    public void close() {
        try {
            detModel.close();
        } catch (Exception ignored) {
        }
        try {
            if (clsModel != null) {
                clsModel.close();
            }
        } catch (Exception ignored) {
        }
        try {
            recModel.close();
        } catch (Exception ignored) {
        }
        engine.close();
    }
}
