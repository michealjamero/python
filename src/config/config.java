package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

 //---------------------------------CONNECTION METHOD----------------------------------
public class config {
    private static void setPreparedStatementValues(PreparedStatement pstmt, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            int idx = i + 1;
            if (v instanceof Integer) {
                pstmt.setInt(idx, (Integer) v);
            } else if (v instanceof Double) {
                pstmt.setDouble(idx, (Double) v);
            } else if (v instanceof Float) {
                pstmt.setFloat(idx, (Float) v);
            } else if (v instanceof Long) {
                pstmt.setLong(idx, (Long) v);
            } else if (v instanceof Boolean) {
                pstmt.setBoolean(idx, (Boolean) v);
            } else if (v instanceof java.util.Date) {
                pstmt.setDate(idx, new java.sql.Date(((java.util.Date) v).getTime()));
            } else if (v instanceof java.sql.Date) {
                pstmt.setDate(idx, (java.sql.Date) v);
            } else if (v instanceof java.sql.Timestamp) {
                pstmt.setTimestamp(idx, (java.sql.Timestamp) v);
            } else {
                pstmt.setString(idx, v != null ? v.toString() : null);
            }
        }
    }

    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            System.out.println("Error hashing password: " + e.getMessage());
            return null;
        }
    }
    public static Connection connectDB() {
        Connection con = null;
        try {
            Class.forName("org.sqlite.JDBC");
            con = DriverManager.getConnection("jdbc:sqlite:C:/Users/user/OneDrive/Pictures/yas/drive-download-20251001T042809Z-1-001/Dishcovery_System.db");

            try (java.sql.Statement stmt = con.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            ensureAllRecipeView(con);
        } catch (Exception e) {
            System.out.println("Connection Failed: " + e);
        }
        return con;
    }
    private static void ensureAllRecipeView(Connection con) {
        try (Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT type FROM sqlite_master WHERE name='allRecipe'")) {
                if (rs.next()) {
                    String type = rs.getString("type");
                    if ("table".equalsIgnoreCase(type)) {
                        stmt.execute("DROP TABLE IF EXISTS allRecipe");
                    } else if ("view".equalsIgnoreCase(type)) {
                        stmt.execute("DROP VIEW IF EXISTS allRecipe");
                    }
                }
            }

            String createViewSql =
                "CREATE VIEW IF NOT EXISTS allRecipe AS \n" +
                "SELECT r.r_title AS r_title, r.r_description AS r_description, r.r_instruction AS r_instruction, r.r_date AS r_date, \n" +
                "       i.i_name AS i_name, i.i_quantity AS i_quantity, i.i_unit AS i_unit, \n" +
                "       c.c_name AS c_name, c.c_description AS c_description \n" +
                "FROM recipe r \n" +
                "LEFT JOIN ingredient i ON i.i_recipeID = r.r_ID \n" +
                "LEFT JOIN category c ON c.c_id = r.category_id";
            stmt.execute(createViewSql);
        } catch (Exception e) {
            System.out.println("⚠️ Could not ensure allRecipe view: " + e.getMessage());
        }
    }
    //---------------------------------ADD RECORD METHOD----------------------------------
    public void addRecord(String sql, Object... values) {
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setPreparedStatementValues(pstmt, values);

            pstmt.executeUpdate();
            System.out.println("Record added successfully!");
        } catch (SQLException e) {
            System.out.println("Error adding record: " + e.getMessage());
        }
    }

    public int executeAndGetKey(String sql, Object... values) {
        int generatedKey = -1;
        
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            setPreparedStatementValues(pstmt, values);
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedKey = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error executing statement: " + e.getMessage());
        }
        
        return generatedKey;
    }
    
    public boolean recordExists(String sql, Object... values) {
        boolean exists = false;
        
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setPreparedStatementValues(pstmt, values);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                exists = rs.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking record existence: " + e.getMessage());
        }
        
        return exists;
    }
    
    public int countRecords(String sql, Object... values) {
        int count = 0;
        
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setPreparedStatementValues(pstmt, values);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error counting records: " + e.getMessage());
        }
        
        return count;
    }
   //---------------------------------VIEW METHOD----------------------------------
    public void viewRecords(String sqlQuery, String[] columnHeaders, String[] columnNames) {
        if (columnHeaders.length != columnNames.length) {
            System.out.println("Error: Mismatch between column headers and column names.");
            return;
        }

        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sqlQuery);
             ResultSet rs = pstmt.executeQuery()) {

            StringBuilder headerLine = new StringBuilder();
            headerLine.append("--------------------------------------------------------------------------------\n| ");
            for (String header : columnHeaders) {
                headerLine.append(String.format("%-20s | ", header));
            }
            headerLine.append("\n--------------------------------------------------------------------------------");

            System.out.println(headerLine.toString());

            while (rs.next()) {
                StringBuilder row = new StringBuilder("| ");
                for (String colName : columnNames) {
                    String value = rs.getString(colName);
                    row.append(String.format("%-20s | ", value != null ? value : ""));
                }
                System.out.println(row.toString());
            }
            System.out.println("--------------------------------------------------------------------------------");

        } catch (SQLException e) {
            System.out.println("Error retrieving records: " + e.getMessage());
        }
    }
    //---------------------------------UPDATE METHOD----------------------------------
     public void updateRecord(String sql, Object... values) {
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setPreparedStatementValues(pstmt, values);

            pstmt.executeUpdate();
            System.out.println("Record updated successfully!");
        } catch (SQLException e) {
            System.out.println("Error updating record: " + e.getMessage());
        }
     }
    
        //---------------------------------DELETE RECORD----------------------------------
     public void deleteRecord(String sql, Object... values) {
    try (Connection conn = this.connectDB();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        setPreparedStatementValues(pstmt, values);

        pstmt.executeUpdate();
        System.out.println("Record deleted successfully!");
    } catch (SQLException e) {
        System.out.println("Error deleting record: " + e.getMessage());
    }
}

public static ResultSet getRecord(String sql, Object... values) {
        try {
            Connection con = connectDB();
            PreparedStatement pst = con.prepareStatement(sql);
            setPreparedStatementValues(pst, values);
            return pst.executeQuery();
        } catch (SQLException e) {
            System.out.println("⚠️ Error fetching record: " + e.getMessage());
            return null;
        }
    }


    public java.util.List<java.util.Map<String, Object>> fetchRecords(String sqlQuery, Object... values) {
    java.util.List<java.util.Map<String, Object>> records = new java.util.ArrayList<>();

    try (Connection conn = this.connectDB();
         PreparedStatement pstmt = conn.prepareStatement(sqlQuery)) {
        setPreparedStatementValues(pstmt, values);

        ResultSet rs = pstmt.executeQuery();
        java.sql.ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnName(i), rs.getObject(i));
            }
            records.add(row);
        }

    } catch (SQLException e) {
        System.out.println("Error fetching records: " + e.getMessage());
    }

    return records;
}

    public double getSingleValue(String sql, Object... params) {
        double result = 0.0;
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setPreparedStatementValues(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    result = rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving single value: " + e.getMessage());
        }
        return result;
    }
}
