package cn.jiebaba.summer.office.ocr;

import java.util.Arrays;

/**
 * 几何运算工具：为 DB 文本检测后处理提供凸包、最小外接矩形（旋转卡壳）、
 * 多边形外扩（unclip）、点序规整与多边形填充等纯 Java 实现，替代 OpenCV/Clipper。
 */
final class Geometry {

    private Geometry() {
    }

    /** 最小外接矩形结果：4 个角点（左上、右上、右下、左下）与短边长度。 */
    static final class MinRect {
        final float[][] corners;
        final float shortSide;

        MinRect(float[][] corners, float shortSide) {
            this.corners = corners;
            this.shortSide = shortSide;
        }
    }

    /**
     * 计算点集的最小外接旋转矩形（等价 cv2.minAreaRect + boxPoints）。
     * <p>对凸包每条边构造对其对齐的外接矩形，取面积最小者，返回 4 角点与短边。
     *
     * @param points 点集扁平数组 [x0,y0,x1,y1,...]
     */
    static MinRect minAreaRect(float[] points) {
        float[] hull = convexHull(points);
        int n = hull.length / 2;
        if (n < 2) {
            float[][] deg = {{0, 0}, {0, 0}, {0, 0}, {0, 0}};
            return new MinRect(deg, 0f);
        }
        double bestArea = Double.POSITIVE_INFINITY;
        float[][] best = null;
        float bestW = 0, bestH = 0;
        for (int i = 0; i < n; i++) {
            float ax = hull[2 * i], ay = hull[2 * i + 1];
            float bx = hull[2 * ((i + 1) % n)], by = hull[2 * ((i + 1) % n) + 1];
            float dx = bx - ax, dy = by - ay;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len == 0f) {
                continue;
            }
            float ux = dx / len, uy = dy / len;
            float nx = -uy, ny = ux;
            float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
            float minN = Float.POSITIVE_INFINITY, maxN = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                float px = hull[2 * j], py = hull[2 * j + 1];
                float pu = px * ux + py * uy;
                float pn = px * nx + py * ny;
                if (pu < minU) minU = pu;
                if (pu > maxU) maxU = pu;
                if (pn < minN) minN = pn;
                if (pn > maxN) maxN = pn;
            }
            double area = (double) (maxU - minU) * (maxN - minN);
            if (area < bestArea) {
                bestArea = area;
                bestW = maxU - minU;
                bestH = maxN - minN;
                float[][] c = new float[4][2];
                c[0] = new float[]{minU * ux + minN * nx, minU * uy + minN * ny};
                c[1] = new float[]{maxU * ux + minN * nx, maxU * uy + minN * ny};
                c[2] = new float[]{maxU * ux + maxN * nx, maxU * uy + maxN * ny};
                c[3] = new float[]{minU * ux + maxN * nx, minU * uy + maxN * ny};
                best = c;
            }
        }
        if (best == null) {
            best = new float[][]{{0, 0}, {0, 0}, {0, 0}, {0, 0}};
        }
        return new MinRect(orderClockwise(best), Math.min(bestW, bestH));
    }

    /** Andrew 单调链求凸包，返回扁平数组 [x0,y0,...]，逆时针序。 */
    static float[] convexHull(float[] points) {
        int n = points.length / 2;
        if (n < 3) {
            return points.clone();
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> {
            float xa = points[2 * a], ya = points[2 * a + 1];
            float xb = points[2 * b], yb = points[2 * b + 1];
            if (xa != xb) return Float.compare(xa, xb);
            return Float.compare(ya, yb);
        });
        float[] hull = new float[2 * n * 2];
        int[] h = new int[n * 2];
        int k = 0;
        for (int pi = 0; pi < n; pi++) {
            float px = points[2 * idx[pi]], py = points[2 * idx[pi] + 1];
            while (k >= 2 && cross(hull, h, k, px, py) <= 0) {
                k--;
            }
            h[k] = pi;
            hull[2 * k] = px;
            hull[2 * k + 1] = py;
            k++;
        }
        int lower = k + 1;
        for (int pi = n - 2; pi >= 0; pi--) {
            float px = points[2 * idx[pi]], py = points[2 * idx[pi] + 1];
            while (k >= lower && cross(hull, h, k, px, py) <= 0) {
                k--;
            }
            h[k] = pi;
            hull[2 * k] = px;
            hull[2 * k + 1] = py;
            k++;
        }
        float[] result = new float[2 * (k - 1)];
        System.arraycopy(hull, 0, result, 0, result.length);
        return result;
    }

    private static float cross(float[] hull, int[] h, int k, float px, float py) {
        float ox = hull[2 * (k - 2)], oy = hull[2 * (k - 2) + 1];
        float ax = hull[2 * (k - 1)], ay = hull[2 * (k - 1) + 1];
        return (ax - ox) * (py - oy) - (ay - oy) * (px - ox);
    }

    /** 多边形面积（绝对值）。 */
    static float polygonArea(float[][] poly) {
        float area = 0f;
        int n = poly.length;
        for (int i = 0; i < n; i++) {
            float[] a = poly[i];
            float[] b = poly[(i + 1) % n];
            area += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(area) / 2f;
    }

    /** 多边形周长。 */
    static float polygonPerimeter(float[][] poly) {
        float peri = 0f;
        int n = poly.length;
        for (int i = 0; i < n; i++) {
            float[] a = poly[i];
            float[] b = poly[(i + 1) % n];
            float dx = b[0] - a[0], dy = b[1] - a[1];
            peri += (float) Math.sqrt(dx * dx + dy * dy);
        }
        return peri;
    }

    /**
     * 多边形外扩（unclip）：按 distance = area*ratio/perimeter 将各边向外偏移，
     * 顶点沿相邻边外法线和的一半方向移动（miter 连接）。等价 Clipper 偏移的近似。
     */
    static float[][] unclip(float[][] box, float ratio) {
        float area = polygonArea(box);
        float peri = polygonPerimeter(box);
        if (peri == 0f) {
            return box;
        }
        float distance = area * ratio / peri;
        int n = box.length;
        float[] cx = new float[n], cy = new float[n];
        for (int i = 0; i < n; i++) {
            cx[i] = box[i][0];
            cy[i] = box[i][1];
        }
        float cenX = 0, cenY = 0;
        for (int i = 0; i < n; i++) {
            cenX += cx[i];
            cenY += cy[i];
        }
        cenX /= n;
        cenY /= n;
        float[][] out = new float[n][2];
        for (int i = 0; i < n; i++) {
            float ax = cx[(i - 1 + n) % n], ay = cy[(i - 1 + n) % n];
            float bx = cx[i], by = cy[i];
            float ccx = cx[(i + 1) % n], ccy = cy[(i + 1) % n];
            float e1x = bx - ax, e1y = by - ay;
            float e2x = ccx - bx, e2y = ccy - by;
            float n1x = -e1y, n1y = e1x;
            float n2x = -e2y, n2y = e2x;
            float midx = (ax + bx) / 2f, midy = (ay + by) / 2f;
            if ((midx - cenX) * n1x + (midy - cenY) * n1y < 0) {
                n1x = -n1x;
                n1y = -n1y;
            }
            float mid2x = (bx + ccx) / 2f, mid2y = (by + ccy) / 2f;
            if ((mid2x - cenX) * n2x + (mid2y - cenY) * n2y < 0) {
                n2x = -n2x;
                n2y = -n2y;
            }
            float dirx = n1x + n2x, diry = n1y + n2y;
            float dlen = (float) Math.sqrt(dirx * dirx + diry * diry);
            if (dlen == 0f) {
                out[i][0] = bx;
                out[i][1] = by;
                continue;
            }
            out[i][0] = bx + dirx / dlen * distance;
            out[i][1] = by + diry / dlen * distance;
        }
        return out;
    }

    /** 将 4 点规整为顺时针 [左上,右上,右下,左下]。 */
    static float[][] orderClockwise(float[][] pts) {
        float[][] s = pts.clone();
        Arrays.sort(s, (a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        float[] left0 = s[0], left1 = s[1];
        float[] right0 = s[2], right1 = s[3];
        float[] tl = left0[1] <= left1[1] ? left0 : left1;
        float[] bl = left0[1] <= left1[1] ? left1 : left0;
        float[] tr = right0[1] <= right1[1] ? right0 : right1;
        float[] br = right0[1] <= right1[1] ? right1 : right0;
        return new float[][]{tl, tr, br, bl};
    }

    /** 射线法判断点是否在多边形内。 */
    static boolean pointInPoly(float x, float y, float[][] poly) {
        boolean inside = false;
        int n = poly.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i][0], yi = poly[i][1];
            float xj = poly[j][0], yj = poly[j][1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
