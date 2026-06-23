import java.sql.Connection;
import java.sql.DriverManager;
public class PgHostsFileTest {
    static String pass = "MjpeDL+s=DY7D@|q";
    public static void main(String[] args) throws Exception {
        String ref = "feodqkgqrxzvcnmuvjgt";
        // 用域名连接，jdk.net.hosts.file 会映射到 IP，SSL 会发 SNI
        String url1 = "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0";
        String url2 = "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require";
        // 也试 sslmode=disable (可能 pooler 不需要 SSL)
        String url3 = "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?prepareThreshold=0";
        String url4 = "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres";

        test(url1, "postgres." + ref);
        test(url2, "postgres." + ref);
        test(url3, "postgres." + ref);
        test(url4, "postgres." + ref);
    }
    static void test(String url, String user) {
        System.out.print(url.substring(0, Math.min(80, url.length())) + " | " + user + " ... ");
        System.out.flush();
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("CONNECTED!");
            var rs = c.createStatement().executeQuery("SELECT version()");
            if (rs.next()) System.out.println("  " + rs.getString(1));
            System.exit(0);
        } catch (Exception e) {
            String m = e.getMessage();
            System.out.println(m.length() > 120 ? m.substring(0,120) : m);
        }
    }
}