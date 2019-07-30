import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

public class CamManager extends Thread {
    private NetworkTableEntry testEntry;
    private NetworkTableEntry targetsEntry;
    private NetworkTableEntry weightsEntry;

    private static CvSink[] ins;
    private static CvSource originalOut; // to computer from robot
    private static CvSource shapesOut; // to computer from robot

    //public static final CamType[] camTypes = new CamType[] {CamType.CAM_LIFE_HD_3000, CamType.CAM_LIFE_HD_3000};

    public static final double H_FOV = 49;
    public static final double V_FOV = 49;

    public CamManager(CameraServer camServer, NetworkTableInstance ntIn, VideoSource[] camsIn, int lenCams) {
        NetworkTableInstance nt = ntIn;
        NetworkTable t = nt.getTable("ShuffleBoard");
        targetsEntry = t.getEntry("targets");
        testEntry = t.getEntry("test");
        weightsEntry = t.getEntry("weights");
        RectCmpWeights.writeDefaults(weightsEntry);

        ins = new CvSink[lenCams];
        if (ins.length == 0) {
            System.err.println("No cameras found, exiting");
            System.exit(1);
        } else if (ins.length > 1) {
            System.out.println("Found " + ins.length + " cameras, using first");
        }
        for (int i = 0; i < camsIn.length; ++i) {
            ins[i] = camServer.getVideo(camsIn[i]);
        }

        originalOut = camServer.putVideo("original", 320, 240);//new CvSource("auto", VideoMode.PixelFormat.kMJPEG, 320, 240, 30);
        MjpegServer s1 = new MjpegServer("serv_original", 8089);
        s1.setSource(originalOut);

        shapesOut = camServer.putVideo("shapes", 320, 240);//new CvSource("line", VideoMode.PixelFormat.kMJPEG, 320, 240, 30);
        MjpegServer s2 = new MjpegServer("serv_shapes", 8090);
        s2.setSource(shapesOut);
    }

    public static final Scalar FILTER_LOW = new Scalar(0, 75, 0);
    public static final Scalar FILTER_HIGH = new Scalar(255, 255, 255);

    public static final int BLUR_THRESH = 60;

    public void run() {
        Mat camFrame = new Mat();
        Mat workingFrame = new Mat();
        Mat lineMap = new Mat();
        while (!Thread.interrupted()) {
            ArrayList<MatOfPoint> contours = new ArrayList<>(); // shapes
            ArrayList<MatOfPoint> contoursFilter = new ArrayList<>(); // filtered shapes

            testEntry.setNumber(System.currentTimeMillis());
            ins[0].grabFrame(camFrame);
            if (!camFrame.empty()) {
                RectCmpWeights weights = RectCmpWeights.readFrom(weightsEntry);

                originalOut.putFrame(camFrame);
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

                int[] matches = VisionCalcs.pairUp(contours, weights);

                for (int i = 0; i < matches.length; i++) {
                    if ((matches[i] != -1) && (matches[i] > i)) {
                        System.out.println(VisionCalcs.getRectPairScore(contours.get(i), contours.get(matches[i]), weights));
                    }
                }

                RotatedRect[] rects = new RotatedRect[contours.size()];
                for (int i = 0; i < rects.length; i++) rects[i] = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
                for (int i = 0; i < rects.length; i++) {
                    VisionCalcs.drawRectangle(lineMap, rects[i], VisionCalcs.COLOR_RED);
                    if ((matches[i] == -1) || (matches[i] < i)) continue;
                    Imgproc.line(lineMap, rects[i].center, rects[matches[i]].center, VisionCalcs.COLOR_RED);
                }
                shapesOut.putFrame(lineMap);

                // Save data
                int numPairs = 0;
                for (int i = 0; i < matches.length; i++) {
                    if (i < matches[i]) numPairs++;
                }
                double[] output = new double[numPairs * 4];
                int wIndex = 0;
                for (int i = 0; i < matches.length; i++) {
                    if (i < matches[i]) {
                        double[] tmp = VisionCalcs.pack(H_FOV, V_FOV, camFrame.size(), rects[i], rects[matches[i]], VisionCalcs.getRectPairScore(contours.get(i), contours.get(matches[i]), weights));
                        output[wIndex++] = tmp[0];
                        output[wIndex++] = tmp[1];
                        output[wIndex++] = tmp[2];
                        output[wIndex++] = tmp[3];
                    }
                }
                targetsEntry.setDoubleArray(output);
                VisionCalcs.wipe(camFrame, new Scalar(0, 0, 0));
                Imgproc.drawContours(camFrame, contoursFilter, -1, VisionCalcs.COLOR_WHITE);
            } else System.err.println("Failed to get stream");
        }
    }
}