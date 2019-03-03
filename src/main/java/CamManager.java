import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class CamManager extends Thread {
    private static CamManager instance = null;
    private NetworkTableInstance nt;
    private NetworkTableEntry testEntry;
    private NetworkTableEntry targetsEntry;

    public static double RATIO_SCORE_THRESH = /*0.5*/20;

    private static CameraServer camServer;
    private static VideoCamera[] cams;

    private static CvSink[] ins;
    private static CvSource autoOut; // to computer from robot
    private static CvSource lineOut; // to computer from robot

    //public static final CamType[] camTypes = new CamType[] {CamType.CAM_LIFE_HD_3000, CamType.CAM_LIFE_HD_3000};

    public static final double H_FOV = 49;
    public static final double V_FOV = 49;

    private CamManager(CameraServer camServer, NetworkTableInstance ntIn, VideoSource[] camsIn, int lenCams) {
        nt = ntIn;
        NetworkTable t = nt.getTable("ShuffleBoard");
        targetsEntry = t.getEntry("targets");
        testEntry = t.getEntry("test");
        cams = new VideoCamera[lenCams];
        System.arraycopy(camsIn, 0, cams, 0, lenCams);
        ins = new CvSink[lenCams];
        for (int i = 0; i < cams.length; ++i) {
            //cams[i] = camServer.startAutomaticCapture((i == 0) ? "left" : "right", i);
            //cams[i].setExposureAuto();
            //cams[i].setResolution(640, 480);
            ins[i] = camServer.getVideo(cams[i]);
        }
        autoOut = camServer.putVideo("auto", 320, 240);//new CvSource("auto", VideoMode.PixelFormat.kMJPEG, 320, 240, 30);
        MjpegServer s1 = new MjpegServer("serv_auto", 8089);
        s1.setSource(autoOut);
        lineOut = camServer.putVideo("line", 320, 240);//new CvSource("line", VideoMode.PixelFormat.kMJPEG, 320, 240, 30);
        MjpegServer s2 = new MjpegServer("serv_line", 8090);
        s2.setSource(lineOut);
    }

    public static CamManager getInstance() {
        return instance;
    }

    public static CamManager init(CameraServer camServer, NetworkTableInstance ntIn, VideoSource[] ins, int len) {
        instance = new CamManager(camServer, ntIn, ins, len);
        return instance;
    }

    //public static final int RHO_TRANSFORM_VALUE = (int) Math.ceil(Math.sqrt(640 * 640 + 480 * 480));

    public static final Scalar FILTER_LOW = new Scalar(0, 75, 0);
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

    /*
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
    */

    private Mat lineMap = new Mat();

    public static boolean startCams() {
        if (instance == null) return false;
        instance.start();
        return true;
    }

    /*
    public void rectSoup(List<RotatedRect> rects, Mat m) {
        int best = -1;
        double v = 0;
        Point[][] allLines = new Point[rects.size()][];
        for (int n = 0; n < rects.size(); n++) {
            RotatedRect r = rects.get(n);
            Point[] ps = new Point[4];
            r.points(ps);
            Point[] lines = new Point[4];
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    lines[i * 2 + j] = VisionCalcs.midpoint(ps[j * 2 + i], ps[(j * 2 + i + 1) % 4]);
                }
            }
            double[] lens = new double[2];
            for (int i = 0; i < 2; i++) {
                lens[i] = VisionCalcs.dist(lines[i * 2], lines[i * 2 + 1]);
            }
            int bestN = (lens[0] > lens[1]) ? 0 : 1;
            allLines[n] = new Point[]{lines[bestN * 2], lines[bestN * 2 + 1]};
            if (lens[bestN] > v) {
                best = n;
                v = lens[bestN];
            }
            //Imgproc.line(m, AutoCamManagerUtil.midpoint(ps[0], ps[1]), AutoCamManagerUtil.midpoint(ps[2], ps[3]), AutoCamManagerUtil.COLOR_RED);
            //Imgproc.line(m, AutoCamManagerUtil.midpoint(ps[1], ps[2]), AutoCamManagerUtil.midpoint(ps[3], ps[0]), AutoCamManagerUtil.COLOR_RED);
        }
        if ((best != -1) && (VisionCalcs.isOk(allLines[best][0].x, allLines[best][0].y, 320, 240, 48) || VisionCalcs.isOk(allLines[best][1].x, allLines[best][1].y, 320, 240, 48))) {
            Imgproc.line(m, allLines[best][0], allLines[best][1], VisionCalcs.COLOR_RED);
            camEntry.setDoubleArray(new double[] {allLines[best][0].x, allLines[best][0].y, allLines[best][1].x, allLines[best][1].y});
            Imgproc.putText(m, String.format("{(%.2f, %.2f),(%.2f, %.2f)}", allLines[best][0].x, allLines[best][0].y, allLines[best][1].x, allLines[best][1].y), VisionCalcs.midpoint(allLines[best]), 0, 0.2, VisionCalcs.COLOR_WHITE);
        }
    }
    */

    public void run() {
        while (!Thread.interrupted()) {
            testEntry.setNumber(System.currentTimeMillis());
            //synchronized (distLock) {
            ins[0].grabFrame(m1); // Uses left camera
            if (!m1.empty()) {
                    /*
                    if ((ins.length > 1) && (ins[1].grabFrame(m2) != 0) && !m2.empty()) {
                        Imgproc.cvtColor(m1, m3, Imgproc.COLOR_BGR2GRAY);
                        Imgproc.cvtColor(m2, distMap, Imgproc.COLOR_BGR2GRAY);
                        distMap.copyTo(m2);
                        distCalc.compute(m3, m2, distMap);
                        distOut.putFrame(distMap);
                        isDistUpdated = true;
                    } else isDistUpdated = false;
                    */
                Imgproc.cvtColor(m1, m2, Imgproc.COLOR_BGR2HLS);                                            // Change color scheme from BGR to HSL
                Core.inRange(m2, FILTER_LOW, FILTER_HIGH, m3);                                                // Filter colors with <250 lightness
                Imgproc.cvtColor(m3, m2, Imgproc.COLOR_GRAY2BGR);                                            // Convert grayscale back to BGR
                //Core.bitwise_or(m1, m2, m3);
                Imgproc.GaussianBlur(m2, m3, new Size(5, 5), 0);                                            // Blur
                Imgproc.threshold(m3, m2, BLUR_THRESH, 255, Imgproc.THRESH_BINARY);                            // Turn colors <60 black, >=60 white
                Imgproc.cvtColor(m3, m2, Imgproc.COLOR_BGR2GRAY);                                            // Convert BGR to grayscale
                //m2.copyTo(lineMap);
                //Imgproc.HoughLines(m2, m3, RHO_TRANSFORM_VALUE, 90, 90);
                Imgproc.findContours(m2, contours, m3, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);    // Find shapes
                lineMap.create(m1.size(), m1.type());
                VisionCalcs.wipe(lineMap, new Scalar(0, 0, 0));
                Imgproc.drawContours(lineMap, contours, -1, VisionCalcs.COLOR_WHITE);
                //System.out.println("Pairing...");
                int[] matches = VisionCalcs.pairUp(contours);
                //System.out.println("Paired");
                for (int i = 0; i < matches.length; i++) {
                    if ((matches[i] != -1) && (matches[i] > i)) {
                        System.out.println(VisionCalcs.getRectPairScore(contours.get(i), contours.get(matches[i])));
                    }
                }
                RotatedRect[] rects = new RotatedRect[contours.size()];
                for (int i = 0; i < rects.length; i++) rects[i] = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
                for (int i = 0; i < rects.length; i++) {
                    VisionCalcs.drawRectangle(lineMap, rects[i], VisionCalcs.COLOR_RED);
                    if ((matches[i] == -1) || (matches[i] < i)) continue;
                    Imgproc.line(lineMap, rects[i].center, rects[matches[i]].center, VisionCalcs.COLOR_RED);
                }
                // Save data
                int numPairs = 0;
                for (int i = 0; i < matches.length; i++) {
                    if (i < matches[i]) numPairs++;
                }
                double[] output = new double[numPairs * 4];
                int wIndex = 0;
                for (int i = 0; i < matches.length; i++) {
                    if (i < matches[i]) {
                        double[] tmp = VisionCalcs.pack(H_FOV, V_FOV, m1.size(), rects[i], rects[matches[i]], VisionCalcs.getRectPairScore(contours.get(i), contours.get(matches[i])));
                        output[wIndex++] = tmp[0];
                        output[wIndex++] = tmp[1];
                        output[wIndex++] = tmp[2];
                        output[wIndex++] = tmp[3];
                    }
                }
                targetsEntry.setDoubleArray(output);
                lineOut.putFrame(lineMap);
                VisionCalcs.wipe(m1, new Scalar(0, 0, 0));
                /*
                for (MatOfPoint c : contours) {
                    //MatOfPoint convexM1 = VisionCalcs.findConvexHull(c);
                    if (Imgproc.contourArea(c) >= 50) {
                        RotatedRect r = Imgproc.minAreaRect(new MatOfPoint2f(c.toArray())); // Get minimum area rectangle / rotated bounding box
                        //double score = AutoCamManagerUtil.scoreRectRatio(r); // Determine how close to the expected ratio the rectangle's sides are
                        if (score <= CamManager.RATIO_SCORE_THRESH) { // It's good enough
                            rects.add(r);
                            /* Bop it */
                            /* Twist it */
                            /* Record it *\/
                            targets.add(r);
                            /* Draw it *\/
                            Point[] box = new Point[4];
                            r.points(box);
                            for (int i = 0; i < 4; i++) {
                                Imgproc.line(m1, box[i], box[(i + 1) % 4], VisionCalcs.COLOR_WHITE);//(score <= RATIO_SCORE_THRESH) ? COLOR_WHITE : COLOR_RED);
                            }
                            /* Write it (the rectangle's "score") *\/
                            Imgproc.putText(m1, String.format("%f", score), r.center, 0, 1, VisionCalcs.COLOR_WHITE);
                        }
                    }
                }
                */
                //Imgproc.drawContours(lineMap, cs, -1, AutoCamManagerUtil.COLOR_RED);
                //rectSoup(rects, lineMap);
                //lineOut.putFrame(lineMap);
                    /*
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
                    */
                // Draw shapes (accepted rectangles from earlier, with the right side ratio)
                Imgproc.drawContours(m1, contoursFilter, -1, VisionCalcs.COLOR_WHITE);
                // Clear array lists
                contours.clear();
                contoursFilter.clear();
                // Output
                autoOut.putFrame(m1);
            } else System.err.println("Failed to get stream");
        }
    }
}