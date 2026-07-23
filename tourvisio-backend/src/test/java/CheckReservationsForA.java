import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckReservationsForA {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            // Get user id for a@gmail.com
            ResultSet rsUser = stmt.executeQuery("SELECT id FROM users WHERE email = 'a@gmail.com'");
            if (rsUser.next()) {
                long userId = rsUser.getLong("id");
                System.out.println("USER ID: " + userId);
                
                // Get reservations
                ResultSet rsRes = stmt.executeQuery("SELECT * FROM reservations WHERE user_id = " + userId);
                int count = 0;
                while (rsRes.next()) {
                    count++;
                    System.out.println("RESERVATION: ID=" + rsRes.getLong("id") + " TOTAL_PRICE=" + rsRes.getDouble("total_price"));
                }
                System.out.println("TOTAL RESERVATIONS FOR a@gmail.com: " + count);
            } else {
                System.out.println("User a@gmail.com not found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
