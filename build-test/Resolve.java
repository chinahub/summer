import java.net.InetAddress;
public class Resolve {
    public static void main(String[] a) throws Exception {
        try {
            InetAddress[] addrs = InetAddress.getAllByName("feodqkgqrxzvcnmuvjgt.supabase.co");
            for (InetAddress addr : addrs) System.out.println("feodqkgqrxzvcnmuvjgt.supabase.co -> " + addr.getHostAddress());
        } catch (Exception e) {
            System.out.println("feodqkgqrxzvcnmuvjgt.supabase.co -> FAIL: " + e.getMessage());
        }
    }
}