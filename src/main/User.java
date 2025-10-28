/*
 * User management class for registration and login
 */
package main;

import config.config;
import java.sql.*;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class User {
    private static Scanner sc = new Scanner(System.in);
    private static config db = new config();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,20}$");


    public static void registerAdmin() {
    System.out.println("\n=== ADMIN SIGN UP ===");
    System.out.print("Full Name(Firstname Lastname): ");
    String name = sc.nextLine();
    String email;
    do {
        System.out.print("Email(example@mail.com): ");
        email = sc.nextLine();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            System.out.println("⚠️ Invalid email format. Please try again.");
        }
    } while (!EMAIL_PATTERN.matcher(email).matches());
    String username;
    do {
        System.out.print("Username (4-20 alphanumeric characters): ");
        username = sc.nextLine();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            System.out.println("⚠️ Username must be 4-20 alphanumeric characters.");
        } else if (usernameExists(username)) {
            System.out.println("⚠️ Username already taken. Please choose another.");
            username = ""; 
        }
    } while (!USERNAME_PATTERN.matcher(username).matches() || usernameExists(username));
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
String hashed = config.hashPassword(password);
String sql = "INSERT INTO users (u_full_name, u_email, u_username, u_password, u_role, u_verified) VALUES (?, ?, ?, ?, ?, ?)";
db.addRecord(sql, name, email, username, hashed, "admin", "0"); 



    System.out.println("✅ Admin account created successfully!");
}

public static void registerUser() {
    System.out.println("\n===== USER REGISTRATION =====");
    System.out.print("Full Name (Firstname Lastname): ");
    String fullName = sc.nextLine();
    String email;
    do {
        System.out.print("Email (example@mail.com): ");
        email = sc.nextLine();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            System.out.println("⚠️ Invalid email format. Please try again.");
        }
    } while (!EMAIL_PATTERN.matcher(email).matches());
    String username;
    do {
        System.out.print("Username (4-20 alphanumeric characters): ");
        username = sc.nextLine();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            System.out.println("⚠️ Username must be 4-20 alphanumeric characters.");
        } else if (usernameExists(username)) {
            System.out.println("⚠️ Username already taken. Please choose another.");
            username = "";
        }
    } while (!USERNAME_PATTERN.matcher(username).matches() || usernameExists(username));
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

    // Save user with plain password
    String hashed = config.hashPassword(password);
    String sql = "INSERT INTO users (u_full_name, u_email, u_username, u_password, u_role) VALUES (?, ?, ?, ?, ?)";
    db.addRecord(sql, fullName, email, username, hashed, "user");

    System.out.println("✅ User registered successfully!");
}
 public static String currentUsername = null;
 public static int currentUserId = -1;
 public static String loginUser() {
    System.out.print("Enter username or email: ");
    String loginId = sc.nextLine();
    System.out.print("Enter password: ");
    String password = sc.nextLine();

    try (Connection con = config.connectDB()) {
        String sql = "SELECT u_id, u_role, u_full_name, u_verified, u_username FROM users WHERE (u_username=? OR u_email=?) AND u_password=?";
        PreparedStatement pst = con.prepareStatement(sql);
        pst.setString(1, loginId);
        pst.setString(2, loginId);
        String hashedLogin = config.hashPassword(password);
        pst.setString(3, hashedLogin); 
        ResultSet rs = pst.executeQuery();

        if (rs.next()) {
            String role = rs.getString("u_role");
            String name = rs.getString("u_full_name");
            currentUserId = rs.getInt("u_id");
            currentUsername = rs.getString("u_username");

            System.out.println("✅ Welcome " + name + "! Role: " + role + " (" + currentUsername + ")");
            return role;
        } else {
            System.out.println("⚠️ Invalid login.");
        }
    } catch (Exception e) {
        System.out.println("⚠️ Error logging in: " + e.getMessage());
    }
    return null;
}


    private static boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE u_username = ?";
        try (Connection conn = db.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.out.println("⚠️ Error checking username: " + e.getMessage());
        }
        return false;
    }

    private static String hashPassword(String password) {
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


public static void approveAdmins() {
    try (Connection con = config.connectDB()) {
        // Show all unverified admins
        String sql = "SELECT u_id, u_full_name, u_email, u_username FROM users WHERE u_role='admin' AND u_verified=0";
        PreparedStatement pst = con.prepareStatement(sql);
        ResultSet rs = pst.executeQuery();

        boolean found = false;
        while (rs.next()) {
            found = true;
            int id = rs.getInt("u_id");
            String name = rs.getString("u_full_name");
            String email = rs.getString("u_email");
            String username = rs.getString("u_username");

            System.out.println("\n[ID: " + id + "] " + name + " (" + username + " | " + email + ")");
        }

        if (!found) {
            System.out.println("✅ No pending admin accounts.");
            return;
        }

        System.out.print("\nEnter the ID of the admin to approve/reject (0 to cancel): ");
        int idChoice = sc.nextInt();
        sc.nextLine(); 

        if (idChoice == 0) return;

        System.out.print("Approve (A) or Reject (R)? ");
        String choice = sc.nextLine().trim().toUpperCase();

        if (choice.equals("A")) {
            String approveSql = "UPDATE users SET u_verified=1 WHERE u_id=?";
            PreparedStatement approvePst = con.prepareStatement(approveSql);
            approvePst.setInt(1, idChoice);
            approvePst.executeUpdate();
            System.out.println("✅ Admin approved successfully!");
        } else if (choice.equals("R")) {
            String rejectSql = "DELETE FROM users WHERE u_id=?";
            PreparedStatement rejectPst = con.prepareStatement(rejectSql);
            rejectPst.setInt(1, idChoice);
            rejectPst.executeUpdate();
            System.out.println("❌ Admin rejected and removed.");
        } else {
            System.out.println("⚠️ Invalid choice.");
        }
    } catch (Exception e) {
        System.out.println("⚠️ Error approving admin: " + e.getMessage());
    }
}
public static void viewApprovedRecipes() {
    Scanner sc = new Scanner(System.in);
    config db = new config();
    db.connectDB();
    String listSql = "SELECT r_ID, r_title, r_description, r_date FROM recipe WHERE LOWER(r_status) = 'approved'";
    String[] headers = {"ID","TITLE","DESCRIPTION","DATE"};
    String[] columns = {"r_ID","r_title","r_description","r_date"};

    int approvedCount = db.countRecords("SELECT COUNT(*) FROM recipe WHERE LOWER(r_status) = 'approved'");
    System.out.println("\n=== APPROVED RECIPES ===");
    db.viewRecords(listSql, headers, columns);

    if (approvedCount == 0) {
        System.out.println("⚠️ No approved recipes yet.");
        return;
    }
    System.out.print("\nEnter Recipe ID to view details (0 to cancel): ");
    String recipeId = sc.nextLine().trim();

    if (recipeId.equals("0")) {
        System.out.println("Returning to menu...");
        return;
    }

    int recipeIdInt;
    try {
        recipeIdInt = Integer.parseInt(recipeId);
    } catch (NumberFormatException nfe) {
        System.out.println("⚠️ Invalid recipe ID.");
        return;
    }
    int existsApproved = db.countRecords("SELECT COUNT(*) FROM recipe WHERE r_ID = ? AND LOWER(r_status) = 'approved'", recipeIdInt);
    if (existsApproved == 0) {
        System.out.println("⚠️ Recipe not found or not approved.");
        return;
    }
    viewRecipe.viewApprovedRecipeDetails(recipeIdInt);
    int totalRatings = db.countRecords("SELECT COUNT(com_rating) FROM Comment_Rating WHERE com_recipeID = ?", recipeIdInt);
    int totalComments = db.countRecords("SELECT COUNT(*) FROM Comment_Rating WHERE com_recipeID = ? AND com_text IS NOT NULL AND TRIM(com_text) <> ''", recipeIdInt);

    double avgRating = 0.0;
    try (java.sql.Connection conn = config.connectDB();
         java.sql.PreparedStatement pst = conn.prepareStatement("SELECT AVG(com_rating) AS avg_rating FROM Comment_Rating WHERE com_recipeID = ?")) {
        pst.setInt(1, recipeIdInt);
        try (java.sql.ResultSet rs = pst.executeQuery()) {
            if (rs.next()) {
                avgRating = rs.getDouble("avg_rating");
            }
        }
    } catch (Exception e) {
        System.out.println("⚠️ Unable to compute rating average: " + e.getMessage());
    }

    System.out.printf("\nRatings: %d   Comments: %d   Average: %.2f/5\n", totalRatings, totalComments, avgRating);
    System.out.println("\n=== Recipe Actions ===");
    System.out.println("1. View comments");
    System.out.println("2. Add comment + rating");
    System.out.println("0. Back");
    int action = -1;
    while (true) {
        System.out.print("Choose an action: ");
        String aStr = sc.nextLine().trim();
        try {
            action = Integer.parseInt(aStr);
            if (action >= 0 && action <= 2) break;
            System.out.println("⚠️ Enter 0, 1, or 2.");
        } catch (NumberFormatException nfe) {
            System.out.println("⚠️ Please enter a valid number.");
        }
    }

    if (action == 1) {
        String commentsSql = "SELECT u.u_username AS owner, cr.com_rating AS rating, cr.com_text AS text " +
                             "FROM Comment_Rating cr JOIN Users u ON u.u_ID = cr.com_userID " +
                             "WHERE cr.com_recipeID = ? AND TRIM(COALESCE(cr.com_text,'')) <> ''";
        try (java.sql.Connection conn = config.connectDB();
             java.sql.PreparedStatement pst = conn.prepareStatement(commentsSql)) {
            pst.setInt(1, recipeIdInt);
            try (java.sql.ResultSet rs = pst.executeQuery()) {
                System.out.println("\n=== COMMENTS ===");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String owner = rs.getString("owner");
                    int r = rs.getInt("rating");
                    String text = rs.getString("text");
                    System.out.println("- " + owner + " rated " + r + "/5");
                    System.out.println("  " + text);
                }
                if (!any) {
                    System.out.println("No comments yet.");
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Unable to load comments: " + e.getMessage());
        }
    } else if (action == 2) {
        if (User.currentUserId <= 0) {
            System.out.println("⚠️ You must be logged in to comment or rate.");
        } else {
            int rating = -1;
            while (true) {
                System.out.print("Enter rating (1-5): ");
                String rStr = sc.nextLine().trim();
                try {
                    rating = Integer.parseInt(rStr);
                    if (rating >= 1 && rating <= 5) {
                        break;
                    } else {
                        System.out.println("⚠️ Rating must be between 1 and 5.");
                    }
                } catch (NumberFormatException nfe) {
                    System.out.println("⚠️ Please enter a number between 1 and 5.");
                }
            }

            System.out.print("Enter your comment (optional, press Enter to skip): ");
            String commentText = sc.nextLine().trim();
            String commentInsertText = commentText.isEmpty() ? "" : commentText;

            
            boolean hasComDate = false;
            boolean comDateRequired = false;
            try (java.sql.Connection conn = config.connectDB();
                 java.sql.PreparedStatement pst = conn.prepareStatement("PRAGMA table_info(Comment_Rating)");
                 java.sql.ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("name");
                    if ("com_date".equalsIgnoreCase(colName)) {
                        hasComDate = true;
                        int notnull = rs.getInt("notnull");
                        String dflt = rs.getString("dflt_value");
                        comDateRequired = (notnull == 1) && (dflt == null);
                    }
                }
            } catch (Exception e) {
                
            }

            if (hasComDate) {
                String comDate = "";
                if (comDateRequired) {
                    while (true) {
                        System.out.print("Enter comment date (YYYYMMDD): ");
                        comDate = sc.nextLine().trim();
                        if (!comDate.isEmpty()) break;
                        System.out.println("⚠️ Date is required.");
                    }
                } else {
                    System.out.print("Enter comment date (YYYYMMDD, optional): ");
                    comDate = sc.nextLine().trim();
                }
                String insertSql = "INSERT INTO Comment_Rating (com_recipeID, com_userID, com_text, com_rating, com_date) VALUES (?,?,?,?,?)";
                try {
                    db.addRecord(insertSql, recipeIdInt, User.currentUserId, commentInsertText, rating, comDate.isEmpty() ? null : comDate);
                    System.out.println("✅ Comment added with rating " + rating + "/5.");
                } catch (Exception e) {
                    System.out.println("⚠️ Unable to add comment/rating: " + (e.getMessage() != null ? e.getMessage() : "Check database constraints."));
                }
            } else {
                String insertSql = "INSERT INTO Comment_Rating (com_recipeID, com_userID, com_text, com_rating) VALUES (?,?,?,?)";
                try {
                    db.addRecord(insertSql, recipeIdInt, User.currentUserId, commentInsertText, rating);
                    System.out.println("✅ Comment added with rating " + rating + "/5.");
                } catch (Exception e) {
                    System.out.println("⚠️ Unable to add comment/rating: " + (e.getMessage() != null ? e.getMessage() : "Check database constraints."));
                }
            }
        }
    }
}
public void shareRecipe() {
    Scanner sc = new Scanner(System.in);
    config db = new config();

    
    System.out.println("\n=== YOUR DRAFT RECIPES ===");
    String userRecipeQuery = "SELECT r_ID, r_title, r_description, r_instruction, r_date, r_status FROM recipe WHERE r_owner = ? AND r_status IS NULL";
    
    try (java.sql.Connection conn = config.connectDB();
         java.sql.PreparedStatement pstmt = conn.prepareStatement(userRecipeQuery)) {
        
        pstmt.setString(1, currentUsername);
        java.sql.ResultSet rs = pstmt.executeQuery();
        
        boolean hasRecipes = false;
        while (rs.next()) {
            hasRecipes = true;
            System.out.println("ID: " + rs.getInt("r_ID") + 
                             " | Title: " + rs.getString("r_title") + 
                             " | Status: Draft");
        }
        
        if (!hasRecipes) {
            System.out.println("❌ You have no draft recipes to share. All your recipes are already shared or create a new recipe first.");
            return;
        }
        
    } catch (Exception e) {
        System.out.println("⚠️ Error loading your draft recipes: " + e.getMessage());
        return;
    }

    System.out.print("\nEnter the Recipe ID you want to share (0 to cancel): ");
    int recipeId = sc.nextInt();
    sc.nextLine();

    if (recipeId == 0) {
        System.out.println("❌ Sharing cancelled.");
        return;
    }

    
    String ownershipCheck = "SELECT r_owner, r_status FROM recipe WHERE r_ID = ?";
    try (java.sql.Connection conn = config.connectDB();
         java.sql.PreparedStatement pstmt = conn.prepareStatement(ownershipCheck)) {
        
        pstmt.setInt(1, recipeId);
        java.sql.ResultSet rs = pstmt.executeQuery();
        
        if (!rs.next()) {
            System.out.println("⚠️ Recipe not found.");
            return;
        }
        
        String recipeOwner = rs.getString("r_owner");
        String recipeStatus = rs.getString("r_status");
        
        if (!currentUsername.equals(recipeOwner)) {
            System.out.println("❌ You can only share your own recipes.");
            return;
        }
        
        if (recipeStatus != null) {
            System.out.println("❌ This recipe has already been shared and has status: " + recipeStatus);
            return;
        }
        
    } catch (Exception e) {
        System.out.println("⚠️ Error verifying recipe ownership: " + e.getMessage());
        return;
    }

    
    System.out.print("Are you sure you want to share Recipe ID " + recipeId + "? (y/n): ");
    String confirm = sc.nextLine();

    if (!confirm.equalsIgnoreCase("y")) {
        System.out.println("❌ Sharing cancelled.");
        return;
    }

    
    String sql = "UPDATE recipe SET r_status = 'Pending' WHERE r_ID = ? AND r_owner = ?";

    try (java.sql.Connection conn = config.connectDB();
         java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

        pstmt.setInt(1, recipeId);
        pstmt.setString(2, currentUsername);
        int rows = pstmt.executeUpdate();

        if (rows > 0) {
            System.out.println("✅ Recipe submitted for approval! Please wait for admin verification.");
        } else {
            System.out.println("⚠️ Could not share recipe. Make sure it belongs to you.");
        }

    } catch (Exception e) {
        System.out.println("⚠️ Error submitting recipe: " + e.getMessage());
    }
}
public static void viewProfile() {
    config db = new config();
    System.out.println("\n=== YOUR PROFILE ===");
    try (java.sql.Connection conn = config.connectDB()) {
        String userSql = "SELECT u_full_name, u_email, u_username, u_role, u_verified FROM users WHERE u_id = ?";
        try (java.sql.PreparedStatement userStmt = conn.prepareStatement(userSql)) {
            userStmt.setInt(1, currentUserId);
            try (java.sql.ResultSet urs = userStmt.executeQuery()) {
                if (urs.next()) {
                    String fullName = urs.getString("u_full_name");
                    String email = urs.getString("u_email");
                    String username = urs.getString("u_username");
                    String role = urs.getString("u_role");
                    int verified = urs.getInt("u_verified");
                    System.out.println("Name     : " + fullName);
                    System.out.println("Email    : " + email);
                    System.out.println("Username : " + username);
                    System.out.println("Role     : " + role);
                } else {
                    System.out.println("⚠️ Unable to load your profile.");
                }
            }
        }

        System.out.println("\n=== YOUR RECIPES ===");
        String listSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                         "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + currentUserId;
        String[] headers = {"ID","TITLE","DESCRIPTION","DATE","STATUS"};
        String[] columns = {"r_ID","r_title","r_description","r_date","r_status"};
        db.viewRecords(listSql, headers, columns);

        int count = db.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = ?", currentUserId);
        if (count == 0) {
            System.out.println("You have not created any recipes yet.");
        } else {
            System.out.print("\nEnter Recipe ID to view details (0 to return): ");
            String idStr = sc.nextLine().trim();
            if (!idStr.equals("0")) {
                try {
                    int recipeId = Integer.parseInt(idStr);
                    viewRecipe.viewOwnedRecipeDetails(recipeId);
                } catch (NumberFormatException nfe) {
                    System.out.println("⚠️ Please enter a valid recipe ID number.");
                }
            } else {
                System.out.println("Returning to menu...");
            }
        }
    } catch (Exception e) {
        System.out.println("⚠️ Error loading profile: " + e.getMessage());
    }
}


    public static void viewMyRecipesTable() {
        try {
            config db = new config();
            db.connectDB();

            System.out.println("\n=== YOUR RECIPES ===");
            String listSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                             "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + currentUserId;
            String[] headers = {"ID","TITLE","DESCRIPTION","DATE","STATUS"};
            String[] columns = {"r_ID","r_title","r_description","r_date","r_status"};
            db.viewRecords(listSql, headers, columns);

            int count = db.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = ?", currentUserId);
            if (count == 0) {
                System.out.println("You have not created any recipes yet.");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error loading your recipes: " + e.getMessage());
        }
    }





}




