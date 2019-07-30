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

    private static VideoCamera[] cams;

    private static CvSink[] ins;
    private static CvSource autoOut; // to computer from robot
    private static CvSource lineOut; // to computer from robot

    //public static final CamType[] camTypes = new CamType[] {CamType.CAM_LIFE_HD_3000, CamType.CAM_LIFE_HD_3000};

    public static final double H_FOV = 49;
    public static final double V_FOV = 49;

    public CamManager(CameraServer camServer, NetworkTableInstance ntIn, VideoSource[] camsIn, int lenCams) {
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

    //public static final int RHO_TRANSFORM_VALUE = (int) Math.ceil(Math.sqrt(640 * 640 + 480 * 480));

    public static final Scalar FILTER_LOW = new Scalar(0, 75, 0);
    public static final Scalar FILTER_HIGH = new Scalar(255, 255, 255);

    public static final int BLUR_THRESH = 60;

    private Mat lineMap = new Mat();

    public void run() {
        while (!Thread.interrupted()) {
            ArrayList<MatOfPoint> contours = new ArrayList<>(); // shapes
            //MatOfPoint2f cTemp2 = new MatOfPoint2f();
            ArrayList<MatOfPoint> contoursFilter = new ArrayList<>(); // filtered shapes
            ArrayList<RotatedRect> targets = new ArrayList<>(); // found rectangles that fill criteria

            testEntry.setNumber(System.currentTimeMillis());
            Mat camFrame = grabFrame(ins[0]);
            Mat workingFrame = new Mat();
            if (!camFrame.empty()) {
                Imgproc.cvtColor(camFrame, workingFrame, Imgproc.COLOR_BGR2HLS);                                            // Change color scheme from BGR to HSL
                Core.inRange(workingFrame, FILTER_LOW, FILTER_HIGH, workingFrame);                                                // Filter colors with <250 lightness
                Imgproc.cvtColor(workingFrame, workingFrame, Imgproc.COLOR_GRAY2BGR);
                Imgproc.GaussianBlur(workingFrame, workingFrame, new Size(5, 5), 0);                                            // Blur
                Imgproc.threshold(workingFrame, workingFrame, BLUR_THRESH, 255, Imgproc.THRESH_BINARY);                            // Turn colors <60 black, >=60 white
                Imgproc.cvtColor(workingFrame, workingFrame, Imgproc.COLOR_BGR2GRAY);
                Imgproc.findContours(workingFrame, contours, workingFrame, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);    // Find shapes
                lineMap.create(camFrame.size(), camFrame.type());
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
                        double[] tmp = VisionCalcs.pack(H_FOV, V_FOV, camFrame.size(), rects[i], rects[matches[i]], VisionCalcs.getRectPairScore(contours.get(i), contours.get(matches[i])));
                        output[wIndex++] = tmp[0];
                        output[wIndex++] = tmp[1];
                        output[wIndex++] = tmp[2];
                        output[wIndex++] = tmp[3];
                    }
                }
                targetsEntry.setDoubleArray(output);
                lineOut.putFrame(lineMap);
                VisionCalcs.wipe(camFrame, new Scalar(0, 0, 0));
                Imgproc.drawContours(camFrame, contoursFilter, -1, VisionCalcs.COLOR_WHITE);
                autoOut.putFrame(camFrame);
            } else System.err.println("Failed to get stream");
        }
    }

    public static Mat grabFrame(CvSink s) {
        Mat m = new Mat();
        s.grabFrame(m);
        return m;
    }
}