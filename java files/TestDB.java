import java.sql.Connection;
import java.sql.SQLException;
import com.onlineexam.dao.DBConnection;

public class TestDB {
    public static void main(String[] args) {
        try (Connection con = DBConnection.getConnection()) {
            if (con != null && !con.isClosed()) {
                System.out.println("Database connected successfully!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}