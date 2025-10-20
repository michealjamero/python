package main;

import config.config;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Admin {
    Scanner sc = new Scanner(System.in);
    config db = new config();

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // Username validation pattern (alphanumeric, 4–20 chars)
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,20}$");

    public void signUpAdminDB() {
        System.out.println("\n=== ADMIN SIGN UP ===");

        // Full name
        System.out.print("Full Name: ");
        String name = sc.nextLine();

        // Email with validation
        String email;
        do {
            System.out.print("Email: ");
            email = sc.nextLine();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                System.out.println("⚠️ Invalid email format. Please try again.");
            }
        } while (!EMAIL_PATTERN.matcher(email).matches());

        // Username with validation
        String username;
        do {
            System.out.print("Username (4-20 alphanumeric characters): ");
            username = sc.nextLine();
            if (!USERNAME_PATTERN.matcher(username).matches()) {
                System.out.println("⚠️ Username must be 4-20 alphanumeric characters.");
            } else if (isUsernameTaken(username)) {
                System.out.println("⚠️ Username already taken. Please choose another.");
                username = ""; // force re-entry
            }
        } while (!USERNAME_PATTERN.matcher(username).matches() || isUsernameTaken(username));

        // Password with confirmation
        String password;
        String confirmPassword;
        do {
            System.out.print("Password (min 6 characters): ");
            password = sc.nextLine();
            System.out.print("Confirm Password: ");
            confirmPassword = sc.nextLine();

            if (password.length() < 6) {
                System.out.println("⚠️ Password must be at least 6 characters long.");
            } else if (!password.equals(confirmPassword)) {
                System.out.println("⚠️ Passwords do not match. Please try again.");
            }
        } while (password.length() < 6 || !password.equals(confirmPassword));

        // Insert into consolidated 'users' table; store plain password to match login
        String sql = "INSERT INTO users (u_full_name, u_email, u_username, u_password, u_role, u_verified) VALUES (?, ?, ?, ?, ?, ?)";
        db.addRecord(sql, name, email, username, password, "admin", 1);

        System.out.println("✅ Admin account created successfully!");
    }

    // Check if username already exists
    private boolean isUsernameTaken(String username) {
        String sql = "SELECT * FROM users WHERE u_username = ?";
        ResultSet rs = db.getRecord(sql, username);
        try {
            return rs != null && rs.next();
        } catch (SQLException e) {
            System.out.println("⚠️ Error checking username: " + e.getMessage());
            return false;
        }
    }

    // Hash password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("⚠️ Error hashing password: " + e.getMessage());
            return password;
        }
    }

    // Approve or reject recipes
    public void reviewRecipes() {
        // Early guard: require pending recipes before review
        int pendingCount = db.countRecords("SELECT COUNT(*) FROM recipe WHERE LOWER(r_status) = 'pending'");
        if (pendingCount == 0) {
            System.out.println("⚠️ No pending recipes to review. Users must share recipes first.");
            return;
        }
    
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
    
        try {
            conn = db.connectDB();
            String selectSql = "SELECT r_id, r_title, r_description, r_instruction, r_date, r_status FROM recipe WHERE r_status = 'Pending'";
    
            pst = conn.prepareStatement(selectSql);
            rs = pst.executeQuery();
    
            boolean hasPending = false;
            while (rs.next()) {
                hasPending = true;
                int id = rs.getInt("r_id");
                String title = rs.getString("r_title");
                String desc = rs.getString("r_description");
                String instr = rs.getString("r_instruction");
                String date = rs.getString("r_date");
                String status = rs.getString("r_status");
    
                System.out.println("\n-------------------------");
                System.out.println("Recipe ID: " + id);
                System.out.println("Title: " + title);
                System.out.println("Description: " + desc);
                System.out.println("Instructions: " + instr);
                System.out.println("Date: " + date);
                System.out.println("Current Status: " + (status == null ? "Pending" : status));
        
                System.out.print("Approve (A) / Reject (R) / Skip (S): ");
                String choice = sc.nextLine().trim().toUpperCase();
    
                if (choice.equals("A")) {
                    updateStatus(conn, id, "Approved");
                } else if (choice.equals("R")) {
                    updateStatus(conn, id, "Rejected");
                } else {
                    System.out.println("Skipped.");
                }
            }

            if (!hasPending) {
                System.out.println("No pending recipes for approval.");
            }

        } catch (SQLException e) {
            System.out.println("⚠️ Error reviewing recipes: " + e.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (pst != null) pst.close();
            } catch (SQLException e) {
                System.out.println("Error closing resources: " + e.getMessage());
            }
        }
    }

    private void updateStatus(Connection conn, int recipeId, String status) throws SQLException {
        String sql = "UPDATE recipe SET r_status = ? WHERE r_id = ?";
        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, status);
            pst.setInt(2, recipeId);
            pst.executeUpdate();
        }
    }
}

