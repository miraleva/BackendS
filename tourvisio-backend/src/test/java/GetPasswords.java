import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class GetPasswords {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            String query = "SELECT id, email, password FROM users WHERE id IN (1, 7)";
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                System.out.println("USER: ID=" + rs.getLong("id") + " EMAIL=[REDACTED] PASS=[REDACTED]");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
