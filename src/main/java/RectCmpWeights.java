import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;

public class RectCmpWeights {
    public final double areaWeight;
    public final double angleWeight;
    public final double areaDiffWeight;
    public final double simWeight;

    private static final double[] DEFAULTS = {5, 1, 0.5, 1};

    public RectCmpWeights() {
        this(DEFAULTS, true);
    }

    public RectCmpWeights(double areaWeightIn, double angleWeightIn, double areaDiffWeightIn, double simWeightIn) {
        areaWeight = areaWeightIn;
        angleWeight = angleWeightIn;
        areaDiffWeight = areaDiffWeightIn;
        simWeight = simWeightIn;
    }

    public RectCmpWeights(double[] data, boolean normalize) {
        if (normalize) {
            data = normalize(data, 4);
        } else {
            if (data.length > 4) {
                throw new IllegalArgumentException("Must give 4 weights");
            }
        }
        areaWeight = data[0];
        angleWeight = data[1];
        areaDiffWeight = data[2];
        simWeight = data[3];
    }

    public static RectCmpWeights readFrom(NetworkTableEntry e) {
        return new RectCmpWeights(e.getDoubleArray((double[]) null), true);
    }

    public static void writeDefaults(NetworkTableEntry e) {
        e.setDoubleArray(DEFAULTS);
    }

    private static double[] normalize(double[] in, int len) {
        if (in == null) {
            return normalize(DEFAULTS, len);
        } else if (in.length == len) {
            return in;
        } else if (in.length > len) {
            double[] ret = new double[len];
            System.arraycopy(in, 0, ret, 0, len);
            return ret;
        } else if (in.length == 0) {
            return normalize(DEFAULTS, len);
        } else {
            double[] ret = new double[len];
            System.arraycopy(in, 0, ret, 0, in.length);
            if (DEFAULTS.length > in.length) {
                System.arraycopy(DEFAULTS, in.length, ret, in.length, DEFAULTS.length - in.length);
            }
            return ret;
        }
    }
}