import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
public class V6ConnTest {
    public static void main(String[] args) throws Exception {
        String v6 = "2406:da14:1d62:b401:c70b:6dbb:da9a:b92d";
        System.out.println("Testing IPv6 TCP connect to " + v6 + ":5432");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getByName(v6), 5432), 10000);
            System.out.println("CONNECTED!");
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
        }
    }
}