import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class GetReservations {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            String query = "SELECT id, user_id, reservation_number, type, item_name, destination, start_date, end_date FROM reservations WHERE user_id IN (1, 7)";
            ResultSet rs = stmt.executeQuery(query);

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("RESERVATION: ID=" + rs.getLong("id") + 
                                   " USER_ID=" + rs.getLong("user_id") + 
                                   " RES_NUM=" + rs.getString("reservation_number") + 
                                   " TYPE=" + rs.getString("type") + 
                                   " ITEM=" + rs.getString("item_name") + 
                                   " DEST=" + rs.getString("destination"));
            }
            if (!found) {
                System.out.println("NO_RESERVATIONS_FOUND");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
