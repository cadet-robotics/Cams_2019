import com.cadet.powerboat9.stableroom.StableRoommate;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class VisionCalcs {
    public static MatOfPoint findConvexHull(MatOfPoint in) {
        MatOfPoint out = new MatOfPoint();
        MatOfInt convex = new MatOfInt();
        Imgproc.convexHull(in, convex, false);
        out.create((int) convex.size().height, 1, CvType.CV_32SC2); // Create empty contour
        for (int i = 0; i < convex.size().height; ++i) {
            int j = (int) convex.get(i, 0)[0];
            out.put(i, 0, in.get(j, 0)[0], in.get(j, 0)[1]); // Convex hull returns a list of points by returning their indexes in the original contour
        }
        return out;
    }

    public static final double MIN_RECT_AREA = 50;

    public static double getRectPairScore(MatOfPoint p1, MatOfPoint p2, RectCmpWeights weights) {
        RotatedRect r1 = normalizeRect(Imgproc.minAreaRect(new MatOfPoint2f(p1.toArray())));
        double area1 = r1.size.area();
        if (area1 < MIN_RECT_AREA) return 0;

        RotatedRect r2 = normalizeRect(Imgproc.minAreaRect(new MatOfPoint2f(p2.toArray())));
        double area2 = r2.size.area();
        if (area2 < MIN_RECT_AREA) return 0;

        // 0 to 1
        double areaScore = ((Imgproc.contourArea(p1) / area1) + (Imgproc.contourArea(p2) / area2)) / 2;
        // 0 to 1
        double areaDiffScore = (area2 > area1) ? (area1 / area2) : (area2 / area1);

        RotatedRect t;
        if (r1.center.x > r2.center.x) {
            t = r1;
            r1 = r2;
            r2 = t;
        }

        Point across = new Point(r2.center.x - r1.center.x, r2.center.y - r1.center.y);
        double angleAcross = Math.atan2(across.y, across.x); // IN RADIANS
        if (angleAcross < 0) angleAcross  = angleAcross + 2 * Math.PI;

        double angle1 = r1.angle * Math.PI / 180;
        double angle2 = r2.angle * Math.PI / 180;

        double angleAcrossPerp = angleAcross + (Math.PI / 2);

        double angleDiff1 = JavaIsCancerChangeMyMind.moduloIsCancer(angle1 - angleAcrossPerp, Math.PI * 2);
        if (angleDiff1 > Math.PI) angleDiff1 = Math.PI * 2 - angleDiff1;
        double angleDiff2 = JavaIsCancerChangeMyMind.moduloIsCancer(angle2 - angleAcrossPerp, Math.PI * 2);
        if (angleDiff2 > Math.PI) angleDiff2 = Math.PI * 2 - angleDiff2;

        if ((angleDiff1 > (Math.PI / 1.5)) || (angleDiff2 > (Math.PI / 1.5))) {
            return 0;
        }

        // 0 to 1
        double angleScore = (angleDiff1 - angleDiff2) / Math.PI;
        if (angleScore < 0) angleScore = -angleScore;
        angleScore = 1 - angleScore;

        // 0 to 1
        double simScore = angleScore - areaDiffScore;
        if (simScore < 0) simScore = -simScore;
        simScore = 1 - simScore;

        return weightedAverage(areaScore, weights.areaWeight, angleScore, weights.angleWeight, areaDiffScore, weights.areaDiffWeight, simScore, weights.simWeight);
    }

    /**
     * Returns a duplicate of a RotatedRect, but with:
     *
     * * height > width
     * * angle >= 270 or angle <= 90
     *
     * @param in The input rectangle
     * @return The "normalized" rectangle
     */
    @SuppressWarnings("SuspiciousNameCombination")
    public static RotatedRect normalizeRect(RotatedRect in) {
        double angle = in.angle;
        double height = in.size.height;
        double width = in.size.width;
        Size newSize;
        if (height > width) {
            newSize = new Size(height, width);
            angle = (angle + 90) % 360;
        } else {
            newSize = in.size;
        }
        if ((angle > 90) && (angle < 270)) {
            angle = (angle + 180) % 360;
        }
        return new RotatedRect(in.center, newSize, angle);
    }

    /**
     * Returns a weighted average
     *
     * @param vars All values and their weights in {num, weight, num, weight, num, weight, ...} form
     * @return A weighted average
     */
    public static double weightedAverage(double... vars) {
        if ((vars.length % 2) != 0) {
            throw new IllegalArgumentException("Must pass even number of arguments");
        }
        double r = 0;
        double div = 0;
        for (int i = 1; i < vars.length; i += 2) {
            r += vars[i - 1] * vars[i];
            div += vars[i];
        }
        if (div == 0) return 0; // r should also be zero, so we'd return 0/0
        return r / div;
    }

    public static double getDistance(double fov, double sizeViewFraction, double sizeMeters) {
        // Get the size of half of the view in meters
        double sizeViewHalf = sizeMeters / sizeViewFraction / 2;
        // Gets the adjacent/distance
        // Converts fov into radians, then divides by 2
        return sizeViewHalf / Math.tan(fov * Math.PI / 360);
    }

    public static final double RECT_LENGTH = 5.5 * 2.54;
    public static final double RECT_WIDTH = 2 * 2.54;

    public static double getDistance(double camHozFov, double camVerFov, Size screenSize, RotatedRect r) {
        double d = 0;
        int cnt = 0;
        boolean isWidthGreater = r.size.width >= r.size.height;
        Rect cmp = (new RotatedRect(ZERO_POINT, isWidthGreater ? (new Size(RECT_LENGTH, RECT_WIDTH)) : (new Size(RECT_WIDTH, RECT_LENGTH)), r.angle)).boundingRect();
        Rect rNonRot = r.boundingRect();
        if (!Double.isNaN(camHozFov)) {
            d = getDistance(camHozFov, rNonRot.width / screenSize.width, cmp.width);
            cnt++;
        }
        if (!Double.isNaN(camVerFov)) {
            d += getDistance(camVerFov, rNonRot.height / screenSize.height, cmp.height);
            cnt++;
        }
        if (cnt == 0) throw new IllegalArgumentException("Need at least one fov angle");
        d /= cnt;
        return d;
    }

    public static double getFractionalAngle(double fov, double screenSize, double pos) {
        boolean isNegative = pos < (screenSize / 2);
        if (isNegative) pos = screenSize - pos;
        else pos = pos - screenSize;
        double commonAdjacent = screenSize / 2 / Math.tan(fov * Math.PI / 180);
        double r = Math.atan(pos / commonAdjacent) / Math.PI * 180;
        return isNegative ? -r : r;
    }

    public static double[] pack(double camHozFov, double camVerFov, Size screen, RotatedRect r1, RotatedRect r2, double score) {
        double[] ret = new double[4];
        Point targetCenter = new Point((r1.center.x + r2.center.x) / 2, (r1.center.y + r2.center.y) / 2);
        ret[0] = getFractionalAngle(camHozFov, screen.width, targetCenter.x);
        ret[1] = getFractionalAngle(camVerFov, screen.height, targetCenter.y);
        ret[2] = (getDistance(camHozFov, camVerFov, screen, r1) + getDistance(camHozFov, camVerFov, screen, r2)) / 2;
        ret[3] = score;
        return ret;
    }

    public static int[] pairUp(List<MatOfPoint> in, RectCmpWeights w) {
        return StableRoommate.runProblem(in.toArray(new MatOfPoint[0]), (v1, v2) -> VisionCalcs.getRectPairScore(v1, v2, w), 1.7);
    }

    public static final Scalar COLOR_WHITE = new Scalar(255, 255, 255);
    public static final Scalar COLOR_RED = new Scalar(0, 0, 255);
    public static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);

    public static <T> int[] getBestPair(List<T> data, BiFunction<T, T, Double> test) {
        int l = data.size();
        if (l < 2) return null;
        int[] best = new int[2];
        best[1] = 1;
        if (l == 2) return best;
        T tempValue = data.get(0);
        double bestV = test.apply(tempValue, data.get(1));
        double t;
        for (int i = 2; i < l; i++) {
            if ((t = test.apply(tempValue, data.get(i))) > bestV) {
                bestV = t;
                best[1] = i;
            }
        }
        for (int i = 1; i < l; i++) {
            tempValue = data.get(i);
            for (int j = i + 1; j < l; j++) {
                if ((t = test.apply(tempValue, data.get(j))) > bestV) {
                    bestV = t;
                    best[0] = i;
                    best[1] = j;
                }
            }
        }
        return best;
    }

    public static <T> ArrayList<int[]> getThresholdPairs(List<T> data, BiFunction<T, T, Double> test, Double min, Double max) {
        int l = data.size();
        if (l < 2) return null;
        ArrayList<int[]> fit = new ArrayList<>();
        T tempValue;
        double t;
        for (int i = 0; i < l; i++) {
            tempValue = data.get(i);
            for (int j = i + 1; j < l; j++) {
                if (((t = test.apply(tempValue, data.get(i))) >= min) && (t <= max)) {
                    fit.add(new int[] {i, j});
                }
            }
        }
        return fit;
    }

    public static final Point ZERO_POINT = new Point(0, 0);

    public static void wipe(Mat m, Scalar c) {
        Size s = m.size();
        Imgproc.rectangle(m, ZERO_POINT, new Point(s.width, s.height), c, -1);
    }

    public static void drawRectangle(Mat m, RotatedRect r, Scalar c) {
        Point[] points = new Point[4];
        r.points(points);
        Imgproc.line(m, points[0], points[1], c);
        Imgproc.line(m, points[1], points[2], c);
        Imgproc.line(m, points[2], points[3], c);
        Imgproc.line(m, points[3], points[0], c);
    }
}