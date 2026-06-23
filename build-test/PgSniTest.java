import java.sql.Connection;
import java.sql.DriverManager;
public class PgSniTest {
    public static void main(String[] args) throws Exception {
        String pass = "MjpeDL+s=DY7D@|q";
        String ref = "feodqkgqrxzvcnmuvjgt";
        // Supavisor 用 SNI 识别租户，需要 SSL + 正确的 SNI hostname
        // Session mode pooler: port 5432, Transaction mode: port 6543
        String[][] tests = {
            // 用域名（DNS 不通但 Java 可能通过 SNI 发送 hostname）
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0", "postgres." + ref},
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require", "postgres." + ref},
            // 直连 IP + ssl + SNI 通过 channelBinding
            {"jdbc:postgresql://52.77.146.31:6543/postgres?sslmode=require&prepareThreshold=0&sslhostnameverifier=org.postgresql.ssl.LazyJavaHostnameVerifier", "postgres." + ref},
        };
        for (String[] t : tests) {
            System.out.print(t[0].substring(0, Math.min(80, t[0].length())) + " ... ");
            try (Connection c = DriverManager.getConnection(t[0], t[1], pass)) {
                System.out.println("CONNECTED!");
                var rs = c.createStatement().executeQuery("SELECT version()");
                if (rs.next()) System.out.println("  " + rs.getString(1));
                return;
            } catch (Exception e) {
                String m = e.getMessage();
                System.out.println(m.length() > 100 ? m.substring(0,100) : m);
            }
        }
    }
}