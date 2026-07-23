import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Date;

public class InsertTestReservations {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tourvisio";
        String user = "postgres";
        String password = "130477";

        String insertResSql = "INSERT INTO reservations (user_id, item_name, type, reservation_number, is_guest, destination, total_price, currency, start_date, end_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(insertResSql)) {
            
            // First record
            pstmt.setLong(1, 1L); // user_id = 1 (a@gmail.com)
            pstmt.setString(2, "Test Paris Uçuşu");
            pstmt.setString(3, "FLIGHT");
            pstmt.setString(4, "RES-TEST001");
            pstmt.setBoolean(5, false);
            pstmt.setString(6, "Paris");
            pstmt.setDouble(7, 1500.00);
            pstmt.setString(8, "EUR");
            pstmt.setDate(9, Date.valueOf("2026-08-01"));
            pstmt.setDate(10, Date.valueOf("2026-08-05"));
            pstmt.executeUpdate();
            
            // Second record
            pstmt.setLong(1, 1L); // user_id = 1 (a@gmail.com)
            pstmt.setString(2, "Test Tokyo Oteli");
            pstmt.setString(3, "HOTEL");
            pstmt.setString(4, "RES-TEST002");
            pstmt.setBoolean(5, false);
            pstmt.setString(6, "Tokyo");
            pstmt.setDouble(7, 4500.00);
            pstmt.setString(8, "USD");
            pstmt.setDate(9, Date.valueOf("2026-09-10"));
            pstmt.setDate(10, Date.valueOf("2026-09-20"));
            pstmt.executeUpdate();
            
            System.out.println("Başarıyla 2 adet test rezervasyonu user_id=1 (a@gmail.com) için eklendi.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
