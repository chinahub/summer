import java.net.Socket;
import java.net.InetSocketAddress;
public class PortTest {
    public static void main(String[] args) {
        // Test which outbound TCP ports work
        String[][] targets = {
            {"8.8.8.8", "53"},     // DNS
            {"8.8.8.8", "443"},    // HTTPS
            {"1.1.1.1", "443"},    // Cloudflare HTTPS
            {"52.77.146.31", "6543"}, // Supabase pooler (resolved earlier)
            {"104.18.38.10", "443"}, // feodqkgqrxzvcnmuvjgt.supabase.co
        };
        for (String[] t : targets) {
            String host = t[0];
            int port = Integer.parseInt(t[1]);
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 5000);
                System.out.println(host + ":" + port + " -> OPEN");
            } catch (Exception e) {
                System.out.println(host + ":" + port + " -> " + e.getMessage());
            }
        }
    }
}