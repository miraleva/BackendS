import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckUsersTable {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            System.out.println("--- USERS TABLE ---");
            ResultSet rs = stmt.executeQuery("SELECT id, email, first_name FROM users");
            while (rs.next()) {
                System.out.println("User ID=" + rs.getLong("id") + " Email=[REDACTED]");
            }
            
            System.out.println("--- RESERVATIONS TABLE (ALL user_ids) ---");
            ResultSet rsRes = stmt.executeQuery("SELECT id, user_id, email FROM reservations");
            while (rsRes.next()) {
                System.out.println("Res ID=" + rsRes.getLong("id") + " user_id=" + rsRes.getLong("user_id") + " email=[REDACTED]");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
