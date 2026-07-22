import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class UpdatePasswords {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            // bcrypt hash for "123456"
            String newHash = "$2a$10$vI8aWNnRpJ0aD0cQ.L43.OoVvR8sF7TpH9p5lE2K4fS2gU9tKqTq.";
            
            String query1 = "UPDATE users SET password = '" + newHash + "' WHERE id IN (1, 7)";
            stmt.executeUpdate(query1);
            
            System.out.println("PASSWORDS_UPDATED");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
