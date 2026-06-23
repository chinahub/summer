import java.sql.Connection;
import java.sql.DriverManager;
public class PgConnTest {
    public static void main(String[] args) throws Exception {
        // 测试1: 直连（原地址）
        test("jdbc:postgresql://db.feodqkgqrxzvcnmuvjgt.supabase.co:5432/postgres", "postgres", "MjpeDL+s=DY7D@|q");
        // 测试2: Supavisor pooler 6543
        test("jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?prepareThreshold=0", "postgres.feodqkgqrxzvcnmuvjgt", "MjpeDL+s=DY7D@|q");
        // 测试3: Session mode pooler 5432
        test("jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres", "postgres.feodqkgqrxzvcnmuvjgt", "MjpeDL+s=DY7D@|q");
    }
    static void test(String url, String user, String pass) {
        System.out.println("Testing: " + url);
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("  CONNECTED! autoCommit=" + c.getAutoCommit());
            var ps = c.createStatement();
            var rs = ps.executeQuery("SELECT version()");
            if (rs.next()) System.out.println("  PG version: " + rs.getString(1));
        } catch (Exception e) {
            System.out.println("  FAIL: " + e.getMessage());
        }
    }
}