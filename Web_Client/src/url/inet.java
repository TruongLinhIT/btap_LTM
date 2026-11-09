package url;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class inet {

    public static void main(String[] args) throws UnknownHostException {

        InetAddress ip = InetAddress.getLocalHost();
        System.out.println("Host Name: " + ip.getHostName());
        System.out.println("IP Address: " + ip.getHostAddress());

        ip = InetAddress.getByName("vku.udn.vn");
        System.out.println("\nHost Name: " + ip.getHostName());
        System.out.println("IP Address: " + ip.getHostAddress());

        System.out.println("\nAll address of google: ");
        InetAddress sw[] = InetAddress.getAllByName("www.tuoitre.vn");
        for (int i = 0; i < sw.length; i++) {
            System.out.println(sw[i]);
        }
    }
}