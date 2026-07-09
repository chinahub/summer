import java.sql.*;
public class DbProbe {
    static String mask(String s) {
        if (s == null) return "null";
        if (s.length() <= 8) return "***(" + s.length() + ")";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 4) + "(" + s.length() + ")";
    }
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres";
        String user = "postgres.feodqkgqrxzvcnmuvjgt";
        String pass = "MjpeDL+s=DY7D@|q";
        try (Connection con = DriverManager.getConnection(url, user, pass)) {
            System.out.println("DB connected: " + con.getMetaData().getDatabaseProductName());
            try (ResultSet rs = con.getMetaData().getColumns(null, "public", "ai_provider_info", null)) {
                System.out.println("== columns ==");
                while (rs.next()) System.out.println("  " + rs.getString("COLUMN_NAME") + " : " + rs.getString("TYPE_NAME"));
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("select * from public.ai_provider_info")) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                System.out.println("== rows ==");
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder("  ");
                    for (int i = 1; i <= n; i++) {
                        String name = md.getColumnLabel(i);
                        String val = rs.getString(i);
                        String low = name.toLowerCase();
                        if (low.contains("key") || low.contains("secret") || low.contains("token") || low.contains("password")) {
                            val = mask(val);
                        }
                        sb.append(name).append("=").append(val).append(" | ");
                    }
                    System.out.println(sb);
                }
            }
        }
    }
}
