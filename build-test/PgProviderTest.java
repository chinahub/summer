import java.sql.Connection;
import java.sql.DriverManager;
public class PgProviderTest {
    static String pass = "MjpeDL+s=DY7D@|q";
    public static void main(String[] args) throws Exception {
        String ref = "feodqkgqrxzvcnmuvjgt";
        String user = "postgres." + ref;
        // 不同 provider 前缀
        String[] prefixes = {"aws-0", "aws-1", "gcp-0", "azure-0"};
        String[] regions = {"ap-southeast-1", "ap-northeast-1", "us-east-1", "us-west-1", "eu-west-1"};
        for (String p : prefixes) {
            for (String r : regions) {
                String host = p + "-" + r + ".pooler.supabase.com";
                String url = "jdbc:postgresql://" + host + ":6543/postgres?prepareThreshold=0&connectTimeout=3";
                try (Connection c = DriverManager.getConnection(url, user, pass)) {
                    System.out.println("CONNECTED via " + host);
                    var rs = c.createStatement().executeQuery("SELECT version()");
                    if (rs.next()) System.out.println("  " + rs.getString(1));
                    return;
                } catch (Exception e) {
                    String m = e.getMessage();
                    if (m.contains("ENOTFOUND")) continue; // wrong pooler, skip
                    if (m.contains("timed out") || m.contains("failed") || m.contains("refused")) continue;
                    System.out.println(host + ": " + (m.length() > 80 ? m.substring(0,80) : m));
                }
            }
        }
        System.out.println("No pooler accepted the tenant");
    }
}