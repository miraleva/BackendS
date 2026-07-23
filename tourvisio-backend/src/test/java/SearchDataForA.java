import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SearchDataForA {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            // Search by email in passengers
            System.out.println("--- SEARCHING PASSENGERS ---");
            ResultSet rsPass = stmt.executeQuery("SELECT p.id, p.reservation_id, p.email, p.first_name FROM passengers p WHERE p.email LIKE '%a@gmail.com%'");
            while (rsPass.next()) {
                System.out.println("Passenger ID=" + rsPass.getLong("id") + " ResID=" + rsPass.getLong("reservation_id") + " Email=[REDACTED]");
            }
            
            // Search by user_id = 1
            System.out.println("--- SEARCHING RESERVATIONS FOR USER_ID=1 ---");
            ResultSet rsRes = stmt.executeQuery("SELECT * FROM reservations WHERE user_id = 1");
            while (rsRes.next()) {
                System.out.println("Reservation ID=" + rsRes.getLong("id"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
