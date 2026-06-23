import java.net.InetAddress;
import java.net.Inet6Address;
public class V6Test {
    public static void main(String[] args) throws Exception {
        String host = "db.feodqkgqrxzvcnmuvjgt.supabase.co";
        System.setProperty("java.net.preferIPv6Addresses", "true");
        System.setProperty("java.net.preferIPv4Stack", "false");
        // Use custom lookup via JNDI DNS
        try {
            var ctx = new javax.naming.directory.InitialDirContext();
            var attrs = ctx.getAttributes("dns:/" + host, new String[]{"AAAA"});
            var attr = attrs.get("AAAA");
            if (attr != null) {
                System.out.println("AAAA records:");
                var ne = attr.getAll();
                while (ne.hasMore()) {
                    String ipv6 = ne.next().toString();
                    System.out.println("  " + ipv6);
                    // try connect
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.connect(new java.net.InetSocketAddress(java.net.InetAddress.getByName(ipv6), 5432), 10000);
                        System.out.println("  TCP OK via " + ipv6);
                    } catch (Exception ce) {
                        System.out.println("  TCP fail: " + ce.getMessage());
                    }
                }
            } else {
                System.out.println("No AAAA record found");
            }
        } catch (Exception e) {
            System.out.println("JNDI DNS failed: " + e.getMessage());
        }
    }
}