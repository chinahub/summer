import java.sql.*;
public class DbProbe {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://db.feodqkgqrxzvcnmuvjgt.supabase.co:5432/postgres";
        try (Connection c = DriverManager.getConnection(url, "postgres", args[0])) {
            System.out.println("CONNECTED");
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select version()")) {
                if (r.next()) System.out.println("VERSION: " + r.getString(1));
            }
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select current_database(), current_user")) {
                if (r.next()) System.out.println("DB/USER: " + r.getString(1) + " / " + r.getString(2));
            }
        }
    }
}
