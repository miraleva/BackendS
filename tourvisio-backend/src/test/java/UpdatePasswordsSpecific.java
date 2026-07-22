import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class UpdatePasswordsSpecific {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            String newHash = "$2a$12$yEH10YLzMY2nS2Rw9aikc.KPeWwf8gfW3XC2Ukb.pZ48CX/35E8sW";
            
            // Updating for user 1 (a@gmail.com)
            String query1 = "UPDATE users SET password = '" + newHash + "' WHERE email = 'a@gmail.com'";
            stmt.executeUpdate(query1);
            
            System.out.println("PASSWORD_UPDATED_FOR_A@GMAIL.COM");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
