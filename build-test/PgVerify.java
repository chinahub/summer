import java.sql.Connection;
import java.sql.DriverManager;
public class PgVerify {
    public static void main(String[] args) throws Exception {
        String url = System.getProperty("ds.url");
        String user = System.getProperty("ds.user");
        String pass = System.getProperty("ds.pass");
        System.out.println("URL: " + url);
        System.out.println("User: " + user);
        System.out.println("Pass length: " + pass.length());
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("CONNECTED!");
            var rs = c.createStatement().executeQuery("SELECT version()");
            if (rs.next()) System.out.println(rs.getString(1));
        }
    }
}