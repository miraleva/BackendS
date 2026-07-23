import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckAllReservations {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            ResultSet rsRes = stmt.executeQuery("SELECT id, user_id, item_name, type FROM reservations");
            int count = 0;
            while (rsRes.next()) {
                count++;
                System.out.println("RESERVATION: ID=" + rsRes.getLong("id") + " USER_ID=" + rsRes.getLong("user_id") + " ITEM=" + rsRes.getString("item_name"));
            }
            System.out.println("TOTAL RESERVATIONS IN DB: " + count);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
