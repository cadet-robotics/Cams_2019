import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import org.opencv.calib3d.StereoBM;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class CamManager extends Thread {
    private static CamManager instance = null;

    public static double RATIO_SCORE_THRESH = 20/*0.5*/;

    private static CameraServer camServer;
    private static VideoCamera[] cams;

    private static CvSink[] ins;
    private static CvSource autoOut; // to computer from robot
    private static CvSource distOut; // to computer from robot

    //public static final CamType[] camTypes = new CamType[] {CamType.CAM_LIFE_HD_3000, CamType.CAM_LIFE_HD_3000};

    private CamManager(CameraServer camServer, VideoSource[] camsIn, int lenCams) {
        cams = new VideoCamera[lenCams];
        System.arraycopy(camsIn, 0, cams, 0, lenCams);
        ins = new CvSink[lenCams];
        for (int i = 0; i < cams.length; ++i) {
            //cams[i] = camServer.startAutomaticCapture((i == 0) ? "left" : "right", i);
            //cams[i].setExposureAuto();
            //cams[i].setResolution(640, 480);
            ins[i] = camServer.getVideo(cams[i]);
        }
        //autoOut = camServer.putVideo("auto", 640, 480);
        distOut = camServer.putVideo("dist", 640, 480);
        autoOut = new CvSource("auto", VideoMode.PixelFormat.kMJPEG, 320, 240, 30);
        MjpegServer s = new MjpegServer("serv", 8089);
        s.setSource(autoOut);
    }

    public static CamManager getInstance() {
        return instance;
    }

    public static CamManager init(CameraServer camServer, VideoSource[] ins, int len) {
        instance = new CamManager(camServer, ins, len);
        return instance;
    }

    public static final int RHO_TRANSFORM_VALUE = (int) Math.ceil(Math.sqrt(640 * 640 + 480 * 480));

    public static final Scalar FILTER_LOW = new Scalar(0, 250, 0);
    public static final Scalar FILTER_HIGH = new Scalar(255, 255, 255);

    public static final int BLUR_THRESH = 60;

    /* These are like registers because assembly is like a security blanket */
    /* Matrixes that store image data */
    private Mat m1 = new Mat();
    private Mat m2 = new Mat();
    private Mat m3 = new Mat();

    private ArrayList<MatOfPoint> contours = new ArrayList<>(); // shapes
    //MatOfPoint2f cTemp2 = new MatOfPoint2f();
    private ArrayList<MatOfPoint> contoursFilter = new ArrayList<>(); // filtered shapes
    private ArrayList<RotatedRect> targets = new ArrayList<>(); // found rectangles that fill criteria

    public static StereoBM distCalc = StereoBM.create(16, 15);

    static {
        // Taken and converted to java from http://www.jayrambhia.com/blog/disparity-mpas
        //sbm.state->SADWindowSize = 9;
        distCalc.setNumDisparities(112);
        distCalc.setPreFilterSize(5);
        distCalc.setPreFilterCap(61);
        distCalc.setMinDisparity(-39);
        distCalc.setTextureThreshold(507);
        distCalc.setUniquenessRatio(0);
        distCalc.setSpeckleWindowSize(0);
        distCalc.setSpeckleRange(8);
        distCalc.setDisp12MaxDiff(1);
    }

    private static Object distLock = new Object();
    private Mat distMap = new Mat();
    private boolean isDistUpdated = false;

    public boolean getDistUpdated() {
        synchronized (distLock) {
            return isDistUpdated;
        }
    }

    public Mat getDistMap() {
        synchronized (distLock) {
            return distMap.clone();
        }
    }

    public static boolean startCams() {
        if (instance == null) return false;
        instance.start();
        return true;
    }

    public static void rectSoup(List<RotatedRect> rects) {
    }

    public void run() {
        while (!Thread.interrupted()) {
            synchronized (distLock) {
                ins[0].grabFrame(m1); // Uses left camera
                if (!m1.empty()) {
                    if ((ins.length > 1) && (ins[1].grabFrame(m2) != 0) && !m2.empty()) {
                        Imgproc.cvtColor(m1, m3, Imgproc.COLOR_BGR2GRAY);
                        Imgproc.cvtColor(m2, distMap, Imgproc.COLOR_BGR2GRAY);
                        distMap.copyTo(m2);
                        distCalc.compute(m3, m2, distMap);
                        distOut.putFrame(distMap);
                        isDistUpdated = true;
                    } else isDistUpdated = false;
                    Imgproc.cvtColor(m1, m2, Imgproc.COLOR_BGR2HLS);                                            // Change color scheme from BGR to HSL
                    Core.inRange(m2, FILTER_LOW, FILTER_HIGH, m3);                                                // Filter colors with <250 lightness
                    Imgproc.cvtColor(m3, m2, Imgproc.COLOR_GRAY2BGR);                                            // Convert grayscale back to BGR
                    //Core.bitwise_or(m1, m2, m3);
                    Imgproc.GaussianBlur(m2, m3, new Size(5, 5), 0);                                            // Blur
                    Imgproc.threshold(m3, m2, BLUR_THRESH, 255, Imgproc.THRESH_BINARY);                            // Turn colors <60 black, >=60 white
                    Imgproc.cvtColor(m3, m2, Imgproc.COLOR_BGR2GRAY);                                            // Convert BGR to grayscale
                    //Imgproc.HoughLines(m2, m3, RHO_TRANSFORM_VALUE, 90, 90);
                    Imgproc.findContours(m2, contours, m3, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);    // Find shapes
                    Imgproc.rectangle(m1, new Point(0, 0), new Point(640, 480), new Scalar(0, 0, 0), -1);        // Clear m1
                    ArrayList<RotatedRect> rects = new ArrayList<>();
                    for (MatOfPoint c : contours) {
                        //MatOfPoint2f cTemp1 = new MatOfPoint2f(c.toArray());
                        //Imgproc.approxPolyDP(cTemp1, cTemp2, Imgproc.arcLength(cTemp1, true) / 1000, true);
                        //if (Imgproc.contourArea(cTemp2) > 500 || true) contoursFilter.add(new MatOfPoint(cTemp2.toArray()));
                        /* calculates convex hulls */
                        if (Imgproc.contourArea(c) >= 50) {
                            MatOfInt convex = new MatOfInt();
                            MatOfPoint convexM1 = new MatOfPoint();
                            Imgproc.convexHull(c, convex, false);
                            convexM1.create((int) convex.size().height, 1, CvType.CV_32SC2); // Create empty contour
                            for (int i = 0; i < convex.size().height; ++i) {
                                int j = (int) convex.get(i, 0)[0];
                                convexM1.put(i, 0, c.get(j, 0)[0], c.get(j, 0)[1]); // Convex hull returns a list of points by returning their indexes in the original contour
                            }
                            RotatedRect r = Imgproc.minAreaRect(new MatOfPoint2f(convexM1.toArray())); // Get minimum area rectangle / rotated bounding box
                            rects.add(r);
                            double score = AutoCamManagerUtil.scoreRectRatio(r); // Determine how close to the expected ratio the rectangle's sides are
                            if (score <= CamManager.RATIO_SCORE_THRESH) { // It's good enough
                                /* Bop it */
                                /* Twist it */
                                /* Record it */
                                targets.add(r);
                                /* Draw it */
                                Point[] box = new Point[4];
                                r.points(box);
                                for (int i = 0; i < 4; i++) {
                                    Imgproc.line(m1, box[i], box[(i + 1) % 4], AutoCamManagerUtil.COLOR_WHITE);//(score <= RATIO_SCORE_THRESH) ? COLOR_WHITE : COLOR_RED);
                                }
                                /* Write it (the rectangle's "score") */
                                Imgproc.putText(m1, String.format("%f", score), r.center, 0, 1, AutoCamManagerUtil.COLOR_WHITE);
                            }
                        }
                    }
                    rectSoup(rects);
                    if (targets.size() > 2) { // We found 2+ viable rectangles
                        // Find the most viable pair
                        double bestScore = 0;
                        int[] best = new int[2];
                        for (int start = 1; start < targets.size(); ++start) {
                            for (int i = start; i < targets.size(); ++i) {
                                double s = AutoCamManagerUtil.scoreDualRectRatio(targets.get(start - 1), targets.get(i)); // Get how well the rectangles are related
                                if (s > bestScore) {
                                    bestScore = s;
                                    best[0] = start - 1;
                                    best[1] = i;
                                }
                            }
                        }
                        // Draw points at their center coordinates
                        for (int i = 0; i < 2; ++i)
                            Imgproc.drawMarker(m1, targets.get(best[i]).center, AutoCamManagerUtil.COLOR_RED);
                    }
                    // Draw shapes (accepted rectangles from earlier, with the right side ratio)
                    Imgproc.drawContours(m1, contoursFilter, -1, AutoCamManagerUtil.COLOR_WHITE);
                    // Clear array lists
                    contours.clear();
                    contoursFilter.clear();
                    // Output
                    autoOut.putFrame(m1);
                } else isDistUpdated = false;
            }
        }
    }
}

class AutoCamManagerUtil {
    public static final double RECTANGLE_TARGET_RATIO = 8;
    public static final double RECTANGLE_DUAL_DIST = 6;
    public static final double RECTANGLE_SIDE_DIST_RATIO = RECTANGLE_TARGET_RATIO / RECTANGLE_DUAL_DIST;

    public static final Scalar COLOR_WHITE = new Scalar(255, 255, 255);
    public static final Scalar COLOR_RED = new Scalar(0, 0, 255);

    public static double scoreRectRatio(RotatedRect r) {
        double rat = r.size.height / r.size.width;
        if (rat < 1) rat = 1 / rat;
        return Math.abs(rat / RECTANGLE_TARGET_RATIO - 1);
    }

    public static double scoreDualRectRatio(RotatedRect r1, RotatedRect r2) {
        double sAdv = (Math.max(r1.size.width, r1.size.height) + Math.max(r2.size.width, r2.size.height)) / 2;
        return Math.abs(dist(r1.center, r2.center) * RECTANGLE_SIDE_DIST_RATIO / sAdv - 1);
    }

    public static double scoreRectDual(RotatedRect r1, RotatedRect r2) {
        double a1 = r1.size.area(), a2 = r2.size.area();
        if (a2 > a1) {
            double t = a2;
            a2 = a1;
            a1 = t;
        }
        return Math.abs(a1 / a2 - 1);
    }

	/*public static double scoreRect(MatOfPoint c) {
		Point[] box = new Point[4];
		Imgproc.minAreaRect(new MatOfPoint2f(c.toArray())).points(box);
		for (int i = 0; i < c.size().height; ++i) {
			c.get(i, 0)
		}
		return 0;
	}

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
}