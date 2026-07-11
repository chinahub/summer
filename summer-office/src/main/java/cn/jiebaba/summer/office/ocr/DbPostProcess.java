package cn.jiebaba.summer.office.ocr;

import java.util.ArrayList;
import java.util.List;

/**
 * DB（Differentiable Binarization）文本检测后处理：将检测模型输出的概率图二值化、
 * 连通域分析、最小外接矩形与外扩，得到文本框。移植自 RapidAI/rapidocr 的 DBPostProcess。
 * <p>关键参数（PP-OCRv4 默认）：thresh=0.3、box_thresh=0.5、unclip_ratio=1.6、use_dilation=true。
 */
final class DbPostProcess {

    private final float thresh;
    private final float boxThresh;
    private final int maxCandidates;
    private final float unclipRatio;
    private final int minSize;
    private final boolean useDilation;

    /** 检测文本框：4 角点（左上、右上、右下、左下）与置信度。 */
    static final class DetBox {
        final float[][] box;
        final float score;

        DetBox(float[][] box, float score) {
            this.box = box;
            this.score = score;
        }
    }

    DbPostProcess(float thresh, float boxThresh, int maxCandidates, float unclipRatio, boolean useDilation) {
        this.thresh = thresh;
        this.boxThresh = boxThresh;
        this.maxCandidates = maxCandidates;
        this.unclipRatio = unclipRatio;
        this.useDilation = useDilation;
        this.minSize = 3;
    }

    /**
     * 对检测模型输出做后处理，得到文本框列表。
     *
     * @param pred    概率图扁平数据，形状 [1,1,H,W]
     * @param predW   概率图宽（模型输入宽）
     * @param predH   概率图高（模型输入高）
     * @param destW   检测输入图宽（缩放回该坐标系）
     * @param destH   检测输入图高
     */
    List<DetBox> process(float[] pred, int predW, int predH, int destW, int destH) {
        boolean[] seg = new boolean[predW * predH];
        for (int i = 0; i < pred.length; i++) {
            seg[i] = pred[i] > thresh;
        }
        if (useDilation) {
            seg = dilate(seg, predW, predH);
        }
        List<int[]> components = connectedComponents(seg, predW, predH);
        List<DetBox> boxes = new ArrayList<>();
        int count = Math.min(components.size(), maxCandidates);
        for (int c = 0; c < count; c++) {
            int[] comp = components.get(c);
            if (comp.length / 2 < 4) {
                continue;
            }
            Geometry.MinRect mr = Geometry.minAreaRect(toFloat(comp));
            if (mr.shortSide < minSize) {
                continue;
            }
            float score = boxScoreFast(pred, predW, predH, mr.corners);
            if (boxThresh > score) {
                continue;
            }
            float[][] expanded = Geometry.unclip(mr.corners, unclipRatio);
            Geometry.MinRect mr2 = Geometry.minAreaRect(flatten(expanded));
            if (mr2.shortSide < minSize + 2) {
                continue;
            }
            float[][] box = scaleClip(mr2.corners, predW, predH, destW, destH);
            boxes.add(new DetBox(box, score));
        }
        return filter(boxes, destW, destH);
    }

    /** 3x3 全核膨胀（近似 cv2.dilate 2x2，效果一致：轻微扩大文本区域）。 */
    private boolean[] dilate(boolean[] seg, int w, int h) {
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean v = false;
                for (int dy = -1; dy <= 1 && !v; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy, nx = x + dx;
                        if (ny >= 0 && ny < h && nx >= 0 && nx < w && seg[ny * w + nx]) {
                            v = true;
                            break;
                        }
                    }
                }
                out[y * w + x] = v;
            }
        }
        return out;
    }

    /** 8 连通域标记，返回每个连通域的像素坐标扁平数组 [x0,y0,x1,y1,...]。 */
    private List<int[]> connectedComponents(boolean[] seg, int w, int h) {
        List<int[]> result = new ArrayList<>();
        int[] label = new int[w * h];
        int[] stack = new int[w * h];
        int[] buf = new int[w * h * 2];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (!seg[idx] || label[idx] != 0) {
                    continue;
                }
                int sp = 0;
                stack[sp++] = idx;
                label[idx] = 1;
                int n = 0;
                while (sp > 0) {
                    int cur = stack[--sp];
                    int cy = cur / w, cx = cur % w;
                    buf[n++] = cx;
                    buf[n++] = cy;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) {
                                continue;
                            }
                            int ny = cy + dy, nx = cx + dx;
                            if (ny < 0 || ny >= h || nx < 0 || nx >= w) {
                                continue;
                            }
                            int ni = ny * w + nx;
                            if (seg[ni] && label[ni] == 0) {
                                label[ni] = 1;
                                stack[sp++] = ni;
                            }
                        }
                    }
                }
                int[] comp = new int[n];
                System.arraycopy(buf, 0, comp, 0, n);
                result.add(comp);
            }
        }
        return result;
    }

    /** box_score_fast：在框多边形内取概率图均值作为置信度。 */
    private float boxScoreFast(float[] pred, int w, int h, float[][] box) {
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (float[] p : box) {
            if (p[0] < minX) minX = p[0];
            if (p[0] > maxX) maxX = p[0];
            if (p[1] < minY) minY = p[1];
            if (p[1] > maxY) maxY = p[1];
        }
        int xmin = clamp((int) Math.floor(minX), 0, w - 1);
        int xmax = clamp((int) Math.ceil(maxX), 0, w - 1);
        int ymin = clamp((int) Math.floor(minY), 0, h - 1);
        int ymax = clamp((int) Math.ceil(maxY), 0, h - 1);
        double sum = 0;
        int cnt = 0;
        for (int y = ymin; y <= ymax; y++) {
            for (int x = xmin; x <= xmax; x++) {
                if (Geometry.pointInPoly(x + 0.5f, y + 0.5f, box)) {
                    sum += pred[y * w + x];
                    cnt++;
                }
            }
        }
        return cnt > 0 ? (float) (sum / cnt) : 0f;
    }

    /** 将框坐标从概率图尺度缩放到检测输入图尺度并裁剪到边界内。 */
    private float[][] scaleClip(float[][] box, int predW, int predH, int destW, int destH) {
        float[][] out = new float[4][2];
        for (int i = 0; i < 4; i++) {
            float sx = Math.round(box[i][0] / predW * destW);
            float sy = Math.round(box[i][1] / predH * destH);
            out[i][0] = clampF(sx, 0, destW);
            out[i][1] = clampF(sy, 0, destH);
        }
        return out;
    }

    /** 过滤过小的框并规整点序。 */
    private List<DetBox> filter(List<DetBox> boxes, int destW, int destH) {
        List<DetBox> out = new ArrayList<>();
        for (DetBox db : boxes) {
            float[][] box = Geometry.orderClockwise(db.box);
            for (int i = 0; i < 4; i++) {
                box[i][0] = clampF(box[i][0], 0, destW - 1);
                box[i][1] = clampF(box[i][1], 0, destH - 1);
            }
            float w1 = dist(box[0], box[1]);
            float h1 = dist(box[0], box[3]);
            if (w1 <= 3 || h1 <= 3) {
                continue;
            }
            out.add(new DetBox(box, db.score));
        }
        return out;
    }

    /** 按阅读顺序排序：先按 y 分行（阈值 10），行内按 x。 */
    static List<DetBox> sortBoxes(List<DetBox> boxes) {
        List<DetBox> sorted = new ArrayList<>(boxes);
        sorted.sort((a, b) -> Float.compare(a.box[0][1], b.box[0][1]));
        List<DetBox> out = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int j = i + 1;
            while (j < sorted.size() && sorted.get(j).box[0][1] - sorted.get(i).box[0][1] < 10) {
                j++;
            }
            List<DetBox> line = new ArrayList<>(sorted.subList(i, j));
            line.sort((a, b) -> Float.compare(a.box[0][0], b.box[0][0]));
            out.addAll(line);
            i = j;
        }
        return out;
    }

    private static float dist(float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float[] toFloat(int[] comp) {
        float[] f = new float[comp.length];
        for (int i = 0; i < comp.length; i++) {
            f[i] = comp[i];
        }
        return f;
    }

    /** 将二维点数组展平为 [x0,y0,x1,y1,...]。 */
    private static float[] flatten(float[][] poly) {
        float[] f = new float[poly.length * 2];
        for (int i = 0; i < poly.length; i++) {
            f[2 * i] = poly[i][0];
            f[2 * i + 1] = poly[i][1];
        }
        return f;
    }
}
