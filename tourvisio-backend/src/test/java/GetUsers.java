import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class GetUsers {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            String query = "SELECT id, email FROM users WHERE email IN ('a@gmail.com', 'miramirzoev@gmail.com')";
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                System.out.println("USER_INFO: ID=" + rs.getLong("id") + " EMAIL=[REDACTED]");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
