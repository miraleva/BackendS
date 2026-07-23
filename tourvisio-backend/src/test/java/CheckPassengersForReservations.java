import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckPassengersForReservations {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            // Get reservations and their passengers
            String query = "SELECT r.id as res_id, r.item_name, p.id as pass_id, p.first_name, p.last_name, p.email " +
                           "FROM reservations r " +
                           "LEFT JOIN passengers p ON r.id = p.reservation_id " +
                           "ORDER BY r.id";
                           
            ResultSet rs = stmt.executeQuery(query);
            
            System.out.println("RESERVATIONS AND PASSENGERS:");
            int resCount = 0;
            int passCount = 0;
            
            while (rs.next()) {
                long resId = rs.getLong("res_id");
                String itemName = rs.getString("item_name");
                long passId = rs.getLong("pass_id");
                
                if (rs.wasNull()) {
                    System.out.println("Reservation ID=" + resId + " (" + itemName + ") -> NO PASSENGERS");
                    resCount++;
                } else {
                    System.out.println("Reservation ID=" + resId + " (" + itemName + ") -> Passenger: [REDACTED]");
                    passCount++;
                }
            }
            
            System.out.println("\nTOTAL PASSENGERS FOUND: " + passCount);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
