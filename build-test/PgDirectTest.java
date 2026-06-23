import java.sql.Connection;
import java.sql.DriverManager;
public class PgDirectTest {
    public static void main(String[] args) throws Exception {
        String pass = "MjpeDL+s=DY7D@|q";
        String ref = "feodqkgqrxzvcnmuvjgt";
        // 用 pooler IP 直连 + SNI/参数
        String[][] tests = {
            {"jdbc:postgresql://52.77.146.31:6543/postgres?prepareThreshold=0", "postgres." + ref},
            {"jdbc:postgresql://52.77.146.31:5432/postgres", "postgres." + ref},
            {"jdbc:postgresql://52.77.146.31:6543/postgres?prepareThreshold=0", "postgres"},
            // 用 options 传递 tenant
            {"jdbc:postgresql://52.77.146.31:6543/postgres?prepareThreshold=0&options=-c%20search_path=public", "postgres." + ref},
        };
        for (String[] t : tests) {
            System.out.print("user=" + t[1] + " ... ");
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