import java.sql.Connection;
import java.sql.DriverManager;
public class PgConnTest2 {
    static String pass = "MjpeDL+s=DY7D@|q";
    static String ref = "feodqkgqrxzvcnmuvjgt";
    public static void main(String[] args) throws Exception {
        String[][] tests = {
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres", "postgres." + ref},
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres", "postgres"},
            {"jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres", "postgres." + ref},
            {"jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres", "postgres." + ref},
            {"jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:6543/postgres", "postgres." + ref},
            {"jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres", "postgres." + ref},
            {"jdbc:postgresql://0.0.0.0.pooler.supabase.com:6543/postgres", "postgres." + ref},
        };
        for (String[] t : tests) {
            test(t[0], t[1]);
        }
    }
    static void test(String url, String user) {
        System.out.print("Testing " + url + " user=" + user + " ... ");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("CONNECTED!");
            var rs = c.createStatement().executeQuery("SELECT version()");
            if (rs.next()) System.out.println("  " + rs.getString(1));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg.length() > 100) msg = msg.substring(0, 100);
            System.out.println("FAIL: " + msg);
        }
    }
}