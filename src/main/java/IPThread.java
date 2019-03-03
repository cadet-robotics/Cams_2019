import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class IPThread extends Thread {
    private NetworkTableInstance nt;
    private NetworkInterface ipInt = null;

    public IPThread(NetworkTableInstance ntIn) {
        nt = ntIn;
        try {
            Enumeration<NetworkInterface> ints = NetworkInterface.getNetworkInterfaces();
            while (ipInt == null) {
                NetworkInterface intTemp = ints.nextElement();
                if (intTemp.isUp() && (!intTemp.isLoopback())) {
                    ipInt = intTemp;
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        setDaemon(true);
    }

    @Override
    public void run() {
        NetworkTable t = nt.getTable("ShuffleBoard");
        NetworkTableEntry e = t.getEntry("raspIP");

        while (!Thread.interrupted()) {
            String addrTxt;
            if (ipInt == null) {
                addrTxt = "[ERROR]";
            } else {
                addrTxt = ipInt.getInetAddresses().nextElement().toString();
            }
            e.setString(addrTxt);
            try {
                sleep(100);
            } catch (InterruptedException ex) {
            }
        }
    }
}