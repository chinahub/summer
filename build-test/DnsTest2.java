import java.net.InetAddress;
public class DnsTest2 {
    public static void main(String[] args) throws Exception {
        String host = "db.feodqkgqrxzvcnmuvjgt.supabase.co";
        // Try all address types
        System.out.println("=== getAllByName ===");
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress a : addrs) {
                System.out.println("  " + a);
            }
        } catch (Exception e) {
            System.out.println("  failed: " + e.getMessage());
        }
        // Force IPv6
        System.out.println("=== preferIPv6 ===");
        System.setProperty("java.net.preferIPv6Addresses", "true");
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress a : addrs) {
                System.out.println("  " + a + " isIPv6=" + (a instanceof java.net.Inet6Address));
            }
            // try TCP connect
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(addrs[0], 5432), 10000);
                System.out.println("  TCP connect OK via " + addrs[0]);
            }
        } catch (Exception e) {
            System.out.println("  failed: " + e.getMessage());
        }
    }
}