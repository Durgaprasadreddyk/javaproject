import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseHelper {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/crudop?serverTimezone=UTC";
    private static final String USER = "root";
    // SECURITY NOTE: Hardcoding passwords is a major security risk.
    // In a real application, use environment variables, a properties file, or a secrets manager.
    private static final String PASSWORD = "Prasad@01";

    // FIXED: Record is a modern, concise way to create an immutable data carrier class.
    // FIXED: The collections returned are now JavaFX ObservableLists to work with TableView.
    public record TableData(List<String> headers, ObservableList<ObservableList<String>> rows) {}

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    // FIXED: All methods now `throw SQLException` so the UI layer can handle errors.
    // FIXED: All methods now use `try-with-resources` to guarantee resource closure.

    public List<String> getTableNames() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (Connection conn = getConnection();
             ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
        }
        return tableNames;
    }

    public List<String> getColumnNames(String tableName) throws SQLException {
        List<String> columnNames = new ArrayList<>();
        // Using LIMIT 0 is an efficient way to get metadata without fetching data.
        String sql = "SELECT * FROM `" + tableName + "` LIMIT 0";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                columnNames.add(metaData.getColumnName(i));
            }
        }
        return columnNames;
    }

    public TableData getTableData(String tableName) throws SQLException {
        List<String> headers = new ArrayList<>();
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        String sql = "SELECT * FROM `" + tableName + "`";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                headers.add(metaData.getColumnName(i));
            }

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getString(i));
                }
                rows.add(row);
            }
        }
        return new TableData(headers, rows);
    }

    public int executeUpdateOrDelete(String sql) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    public void insertRow(String tableName, Map<String, String> data) throws SQLException {
        // Build the query dynamically but safely using PreparedStatement
        String columns = data.keySet().stream().map(key -> "`" + key + "`").collect(Collectors.joining(", "));
        String placeholders = String.join(", ", Collections.nCopies(data.size(), "?"));
        String sql = "INSERT INTO `" + tableName + "` (" + columns + ") VALUES (" + placeholders + ")";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (String value : data.values()) {
                stmt.setString(index++, value);
            }
            stmt.executeUpdate();
        }
    }

    public TableData executeGenericQuery(String sql) throws SQLException {
        List<String> headers = new ArrayList<>();
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                headers.add(metaData.getColumnName(i));
            }

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getString(i));
                }
                rows.add(row);
            }
        }
        return new TableData(headers, rows);
    }

    public void updateCellValue(String tableName, String columnName, String newValue, String keyColumn, String keyValue) throws SQLException {
        String sql = "UPDATE `" + tableName + "` SET `" + columnName + "` = ? WHERE `" + keyColumn + "` = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newValue);
            stmt.setString(2, keyValue);
            stmt.executeUpdate();
        }
    }

    public void deleteMultipleRows(String tableName, String keyColumn, List<String> keyValues) throws SQLException {
        if (keyValues == null || keyValues.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(keyValues.size(), "?"));
        String sql = "DELETE FROM `" + tableName + "` WHERE `" + keyColumn + "` IN (" + placeholders + ")";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < keyValues.size(); i++) {
                stmt.setString(i + 1, keyValues.get(i));
            }
            stmt.executeUpdate();
        }
    }
}