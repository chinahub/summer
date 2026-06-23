import java.io.*;
import java.net.*;
public class DohTest {
    public static void main(String[] args) throws Exception {
        // Query Google DoH for AAAA record via IP 8.8.8.8 (ICMP confirmed reachable)
        String host = "db.feodqkgqrxzvcnmuvjgt.supabase.co";
        String dohUrl = "https://8.8.8.8/resolve?name=" + host + "&type=AAAA";
        try {
            URL url = new URL(dohUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("accept", "application/dns-json");
            int code = conn.getResponseCode();
            System.out.println("HTTP " + code);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) System.out.println(line);
        } catch (Exception e) {
            System.out.println("DoH failed: " + e.getMessage());
        }
    }
}