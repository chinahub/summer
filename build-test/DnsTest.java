import java.net.InetAddress;
public class DnsTest {
    public static void main(String[] args) throws Exception {
        String host = "db.feodqkgqrxzvcnmuvjgt.supabase.co";
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress a : addrs) {
                System.out.println("Resolved: " + a.getHostAddress());
            }
        } catch (Exception e) {
            System.out.println("DNS failed: " + e.getMessage());
        }
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, 5432), 10000);
            System.out.println("TCP connect OK to " + host + ":5432");
        } catch (Exception e) {
            System.out.println("TCP failed: " + e.getMessage());
        }
    }
}