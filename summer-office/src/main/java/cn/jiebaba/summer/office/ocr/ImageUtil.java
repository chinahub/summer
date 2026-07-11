package cn.jiebaba.summer.office.ocr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 图像处理工具：基于纯 JDK（{@link ImageIO} + 手写像素运算）实现 OCR 流水线所需的
 * 解码、双线性缩放、透视矫正、旋转与归一化，零第三方图像库依赖。
 * <p>图像内部以 {@link Img} 表示：宽高与 RGB 像素数组（每像素 0xRRGGBB 打包，行优先）。
 */
final class ImageUtil {

    private ImageUtil() {
    }

    /** 轻量图像：宽、高与 RGB 像素（0xRRGGBB，行优先）。 */
    static final class Img {
        final int width;
        final int height;
        final int[] rgb;

        Img(int width, int height, int[] rgb) {
            this.width = width;
            this.height = height;
            this.rgb = rgb;
        }
    }

    /** 将图片字节（PNG/JPEG/BMP 等）解码为 RGB 图像。 */
    static Img decode(byte[] data) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) {
                throw new OcrException("无法解码图片，可能为不支持的格式");
            }
            return fromBuffered(src);
        } catch (IOException e) {
            throw new OcrException("图片解码失败", e);
        }
    }

    /** 将 {@link BufferedImage} 转为 {@link Img}，灰度图自动扩展为三通道等值。 */
    private static Img fromBuffered(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] rgb = src.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < rgb.length; i++) {
            int c = rgb[i];
            if ((c >>> 24) == 0) {
                rgb[i] = 0xFFFFFF;
            } else {
                rgb[i] = c & 0xFFFFFF;
            }
        }
        return new Img(w, h, rgb);
    }

    /** 双线性缩放至目标尺寸。 */
    static Img resize(Img src, int newW, int newH) {
        if (src.width == newW && src.height == newH) {
            return src;
        }
        int[] out = new int[newW * newH];
        float xRatio = (float) src.width / newW;
        float yRatio = (float) src.height / newH;
        for (int y = 0; y < newH; y++) {
            float sy = (y + 0.5f) * yRatio - 0.5f;
            int y0 = (int) Math.floor(sy);
            int y1 = y0 + 1;
            float fy = sy - y0;
            y0 = clamp(y0, 0, src.height - 1);
            y1 = clamp(y1, 0, src.height - 1);
            for (int x = 0; x < newW; x++) {
                float sx = (x + 0.5f) * xRatio - 0.5f;
                int x0 = (int) Math.floor(sx);
                int x1 = x0 + 1;
                float fx = sx - x0;
                x0 = clamp(x0, 0, src.width - 1);
                x1 = clamp(x1, 0, src.width - 1);
                out[y * newW + x] = bilinear(src.rgb, x0, x1, y0, y1, fx, fy, src.width);
            }
        }
        return new Img(newW, newH, out);
    }

    /** 四点双线性采样。 */
    private static int bilinear(int[] rgb, int x0, int x1, int y0, int y1, float fx, float fy, int w) {
        int p00 = rgb[y0 * w + x0];
        int p01 = rgb[y0 * w + x1];
        int p10 = rgb[y1 * w + x0];
        int p11 = rgb[y1 * w + x1];
        int r = blend(blend(p00 >> 16 & 0xFF, p01 >> 16 & 0xFF, fx), blend(p10 >> 16 & 0xFF, p11 >> 16 & 0xFF, fx), fy);
        int g = blend(blend(p00 >> 8 & 0xFF, p01 >> 8 & 0xFF, fx), blend(p10 >> 8 & 0xFF, p11 >> 8 & 0xFF, fx), fy);
        int b = blend(blend(p00 & 0xFF, p01 & 0xFF, fx), blend(p10 & 0xFF, p11 & 0xFF, fx), fy);
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * 透视矫正裁剪：将四边形 box（4 点，顺序 左上、右上、右下、左下）校正为矩形并采样。
     * <p>计算 rect->box 的单应矩阵，对输出每个像素反向映射回源图做双线性采样，越界采用边缘复制（replicate）。
     *
     * @param src  源图像
     * @param box  4 个角点，每点 {x, y}
     * @param outW 输出宽度（取四边形上下边长较大值）
     * @param outH 输出高度（取四边形左右边长较大值）
     */
    static Img warpPerspective(Img src, float[][] box, int outW, int outH) {
        float[][] rect = {{0, 0}, {outW, 0}, {outW, outH}, {0, outH}};
        float[] h = findHomography(rect, box);
        int[] out = new int[outW * outH];
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                float denom = h[6] * x + h[7] * y + 1f;
                float sx = (h[0] * x + h[1] * y + h[2]) / denom;
                float sy = (h[3] * x + h[4] * y + h[5]) / denom;
                out[y * outW + x] = sampleReplicate(src, sx, sy);
            }
        }
        return new Img(outW, outH, out);
    }

    /** 双线性采样，越界取最近边缘像素。 */
    private static int sampleReplicate(Img src, float sx, float sy) {
        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        if (sx < 0) {
            sx = 0;
        } else if (sx > src.width - 1) {
            sx = src.width - 1;
        }
        if (sy < 0) {
            sy = 0;
        } else if (sy > src.height - 1) {
            sy = src.height - 1;
        }
        int x1i = (int) Math.floor(sx);
        int y1i = (int) Math.floor(sy);
        int x2i = x1i + 1;
        int y2i = y1i + 1;
        float fx = sx - x1i;
        float fy = sy - y1i;
        x1i = clamp(x1i, 0, src.width - 1);
        x2i = clamp(x2i, 0, src.width - 1);
        y1i = clamp(y1i, 0, src.height - 1);
        y2i = clamp(y2i, 0, src.height - 1);
        return bilinear(src.rgb, x1i, x2i, y1i, y2i, fx, fy, src.width);
    }

    /**
     * 求解 4 组点对应的单应矩阵（src->dst），返回 8 个参数 [h0..h7]，h8=1。
     * <p>对每组 (x,y)->(X,Y) 建立两个方程，8 元线性方程组用高斯消元求解。
     */
    private static float[] findHomography(float[][] src, float[][] dst) {
        float[][] a = new float[8][8];
        float[] b = new float[8];
        for (int i = 0; i < 4; i++) {
            float x = src[i][0];
            float y = src[i][1];
            float xs = dst[i][0];
            float ys = dst[i][1];
            int r = i * 2;
            a[r][0] = x; a[r][1] = y; a[r][2] = 1;
            a[r][6] = -x * xs; a[r][7] = -y * xs;
            b[r] = xs;
            a[r + 1][3] = x; a[r + 1][4] = y; a[r + 1][5] = 1;
            a[r + 1][6] = -x * ys; a[r + 1][7] = -y * ys;
            b[r + 1] = ys;
        }
        return solveLinear(a, b);
    }

    /** 高斯消元法解 8x8 线性方程组 Ax=b。 */
    private static float[] solveLinear(float[][] a, float[] b) {
        int n = b.length;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            float[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            float tb = b[col];
            b[col] = b[pivot];
            b[pivot] = tb;
            float pv = a[col][col];
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                float factor = a[r][col] / pv;
                if (factor == 0f) {
                    continue;
                }
                for (int c = col; c < n; c++) {
                    a[r][c] -= factor * a[col][c];
                }
                b[r] -= factor * b[col];
            }
        }
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = b[i] / a[i][i];
        }
        return x;
    }

    /** 顺时针旋转 180 度。 */
    static Img rotate180(Img src) {
        int w = src.width;
        int h = src.height;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[(h - 1 - y) * w + (w - 1 - x)] = src.rgb[y * w + x];
            }
        }
        return new Img(w, h, out);
    }

    /** 逆时针旋转 90 度（等价 numpy.rot90）。 */
    static Img rotate90(Img src) {
        int w = src.width;
        int h = src.height;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[(w - 1 - x) * h + y] = src.rgb[y * w + x];
            }
        }
        return new Img(h, w, out);
    }

    /** 在四周填充指定像素数，填充值为指定颜色。 */
    static Img pad(Img src, int top, int bottom, int left, int right, int fill) {
        int newW = src.width + left + right;
        int newH = src.height + top + bottom;
        int[] out = new int[newW * newH];
        if (fill != 0) {
            java.util.Arrays.fill(out, fill);
        }
        for (int y = 0; y < src.height; y++) {
            System.arraycopy(src.rgb, y * src.width, out, (y + top) * newW + left, src.width);
        }
        return new Img(newW, newH, out);
    }

    /**
     * 转为 CHW 浮点并归一化：(pixel/255 - mean) / std，按通道独立。
     *
     * @param mean 各通道均值（0~1）
     * @param std  各通道标准差（0~1）
     * @return 长度 3*H*W 的浮点数组，布局 [channel][y][x]
     */
    static float[] toChwNormalized(Img src, float[] mean, float[] std) {
        int w = src.width;
        int h = src.height;
        float[] out = new float[3 * w * h];
        int plane = w * h;
        for (int i = 0; i < plane; i++) {
            int c = src.rgb[i];
            float r = ((c >> 16 & 0xFF) / 255f - mean[0]) / std[0];
            float g = ((c >> 8 & 0xFF) / 255f - mean[1]) / std[1];
            float b = ((c & 0xFF) / 255f - mean[2]) / std[2];
            out[i] = r;
            out[plane + i] = g;
            out[plane * 2 + i] = b;
        }
        return out;
    }
}
