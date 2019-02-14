import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

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

    public static double getRectPairScore(MatOfPoint p1, MatOfPoint p2) {
        RotatedRect r1 = Imgproc.minAreaRect(new MatOfPoint2f(p1.toArray()));
        double areaScore = Math.abs(r1.size.area() / Imgproc.contourArea(p1) - 1);
        RotatedRect r2 = Imgproc.minAreaRect(new MatOfPoint2f(p2.toArray()));
        areaScore += Math.abs(r2.size.area() / Imgproc.contourArea(p2) - 1);
        areaScore = 1 / (areaScore + 1);
        RotatedRect t;
        if (r1.center.x > r2.center.x) {
            t = r1;
            r1 = r2;
            r2 = t;
        }
        Point across = new Point(r2.center.x - r1.center.x, r2.center.y - r1.center.y);
        double angleAcross = Math.atan2(across.y, across.x); // IN RADIANS
        double angleAcrossPerp = JavaIsCancerChangeMyMind.moduloIsCancer(angleAcross - Math.PI / 2, Math.PI * 2);
        double angle1 = r1.angle * Math.PI / 180;
        double angleDiff1 = JavaIsCancerChangeMyMind.moduloIsCancer(angle1 - angleAcrossPerp, Math.PI * 2);
        if (angleDiff1 > Math.PI) angleDiff1 = Math.PI * 2 - angleDiff1;
        double angle2 = r2.angle * Math.PI / 180;
        double angleDiff2 = JavaIsCancerChangeMyMind.moduloIsCancer(angle2 - angleAcrossPerp, Math.PI * 2);
        if (angleDiff2 > Math.PI) angleDiff2 = Math.PI * 2 - angleDiff2;
        double angleScore = (angleDiff1 + angleDiff2) / Math.PI / 4;
        return areaScore * angleScore;
    }

    public static double getDistance(double camHozFov, double sizeViewFraction, double sizeM) {
        // Get the size of the view in meters
        double sizeView = sizeM / sizeViewFraction / 2;
        // Gets the adjacent/distance
        // Converts camHozFov into radians, then divides by 2
        return sizeView / Math.tan(camHozFov * Math.PI / 360);
    }

    public static int[] pairUp(List<MatOfPoint> in) {
        return StableRoommate.runProblem(in.toArray(new MatOfPoint[0]), VisionCalcs::getRectPairScore);
    }

    public static final double RECTANGLE_TARGET_RATIO = 8;
    public static final double RECTANGLE_DUAL_DIST = 6;
    public static final double RECTANGLE_SIDE_DIST_RATIO = RECTANGLE_TARGET_RATIO / RECTANGLE_DUAL_DIST;

    public static final Scalar COLOR_WHITE = new Scalar(255, 255, 255);
    public static final Scalar COLOR_RED = new Scalar(0, 0, 255);
    public static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);

	/*
	public static double findDistToLine(Point p1, Point p2, Point loc) {
		if (p1.x == p2.x) {
			// Points have same x
			if (p1.y == p2.y) {
				// Points are identical
				return dist(loc, p2);
			} else if (p1.y > p2.y) {
				// Make p2.y > p1.y
				Point t = p1;
				p1 = p2;
				p2 = t;
			}
			if (loc.y > p2.y) {
				// Location is above top point
				return dist(loc, p2);
			} else if (loc.y < p1.y) {
				// Location is below bottom point
				return dist(p1, loc);
			} else {
				// Location is between both point's y positions
				if (p1.x > loc.x) {
					return p1.x - loc.x;
				} else {
					return loc.x - p1.x;
				}
			}
		} else if (p1.x > p2.x) {
			// Make p2.x > p1.x
			Point t = p1;
			p1 = p2;
			p2 = t;
		}
		double m = (p2.y - p1.y) / (p2.x - p1.x);
		double b = p1.y - p1.x * m;
		double mp = -(1 / m);
		double bp = loc.y - loc.x * mp;
		//mx + b = mpx + bp
		//mx - (mp)x = (bp) - b
		//(m - (mp))x = (bp) - b
		//x = [(bp) - b] / [m - (mp)]
		double iX = (bp - b) / (m - mp);
		double iY = m * iX;
		if (iX < p1.x) {
			// Intersect is to the left of line
			return dist(p1, loc);
		} else if (iX > p2.x) {
			// Intersect is to the right of line
			return dist(loc, p2);
		} else {
			// Intersect is on line
			return dist(loc, new Point(iX, iY));
		}
	}*/

    public static double distSq(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return dx * dx + dy * dy;
    }

    public static double dist(Point p1, Point p2) {
        return Math.sqrt(distSq(p1, p2));
    }

    public static Point midpoint(Point... ps) {
        double x = 0, y = 0;
        for (Point p : ps) {
            x += p.x;
            y += p.y;
        }
        return new Point(x / ps.length, y / ps.length);
    }

    /*
    public static double getArea(MatOfPoint mat) {
        Point m = midpoint(mat.toArray());
        double t = 0;
        Point[] ps = mat.toArray();
        for (int i = 0; i < ps.length; i++) {
            t += trigArea(ps[i], ps[(i + 1) % ps.length], m);
        }
        return t;
    }

    public static double trigArea(Point... ps) {
        double[] sides = new double[3];
        for (int i = 0; i < 3; i++) sides[i] = dist(ps[i], ps[(i + 1) % 3]);
        double ph = (sides[0] + sides[1] + sides[2]) / 2;
        return Math.sqrt(ph * (ph - sides[0]) * (ph - sides[1]) * (ph - sides[2]));
    }
    */

    public static boolean isOk(double x, double y, double sx, double sy, double r) {
        if ((x < r) || (x > (sx - r))) return false;
        if ((y < r) || (y > (sy - r))) return false;
        return true;
    }

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
}
