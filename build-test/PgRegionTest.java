import java.sql.Connection;
import java.sql.DriverManager;
public class PgRegionTest {
    static String ref = "feodqkgqrxzvcnmuvjgt";
    static String pass = "MjpeDL+s=DY7D@|q";
    public static void main(String[] args) throws Exception {
        test("us-east-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("us-west-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("us-west-2", "postgres.feodqkgqrxzvcnmuvjgt");
        test("eu-west-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("eu-west-2", "postgres.feodqkgqrxzvcnmuvjgt");
        test("eu-central-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("ap-southeast-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("ap-southeast-2", "postgres.feodqkgqrxzvcnmuvjgt");
        test("ap-northeast-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("ap-south-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("ca-central-1", "postgres.feodqkgqrxzvcnmuvjgt");
        test("sa-east-1", "postgres.feodqkgqrxzvcnmuvjgt");
    }
    static void test(String region, String user) {
        String url = "jdbc:postgresql://aws-0-" + region + ".pooler.supabase.com:6543/postgres?prepareThreshold=0&connectTimeout=5";
        System.out.print(region + ": ");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("CONNECTED!");
            return;
        } catch (Exception e) {
            String m = e.getMessage();
            if (m.length() > 80) m = m.substring(0, 80);
            System.out.println(m);
        }
    }
}