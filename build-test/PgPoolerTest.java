import java.sql.Connection;
import java.sql.DriverManager;
public class PgPoolerTest {
    static String pass = "MjpeDL+s=DY7D@|q";
    public static void main(String[] args) throws Exception {
        String ref = "feodqkgqrxzvcnmuvjgt";
        // Supabase 新版 pooler 格式可能是 <ref>.pooler.supabase.com
        // 或 db.<ref>.pooler.supabase.com
        String[][] tests = {
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres", "postgres." + ref},
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres", "postgres." + ref},
            // 试旧的 pooler 格式
            {"jdbc:postgresql://db." + ref + ".pooler.supabase.com:6543/postgres", "postgres"},
            {"jdbc:postgresql://db." + ref + ".pooler.supabase.com:5432/postgres", "postgres"},
            // 试用 channel binding / sni
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require", "postgres." + ref},
        };
        for (String[] t : tests) {
            System.out.print(t[0] + " | user=" + t[1] + " ... ");
            System.out.flush();
            try (Connection c = DriverManager.getConnection(t[0], t[1], pass)) {
                System.out.println("CONNECTED!");
                var rs = c.createStatement().executeQuery("SELECT version()");
                if (rs.next()) System.out.println("  " + rs.getString(1));
                return;
            } catch (Exception e) {
                String m = e.getMessage();
                System.out.println(m.length() > 120 ? m.substring(0,120) : m);
            }
        }
        System.out.println("All attempts failed");
    }
}