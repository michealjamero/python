
package main;

import config.config;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class category {
    
    private static void ensureCategoryColumn() {
        try (Connection conn = config.connectDB();
             PreparedStatement check = conn.prepareStatement("PRAGMA table_info(recipe)");
             ResultSet rs = check.executeQuery()) {
            boolean hasCategoryId = false;
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("category_id".equalsIgnoreCase(colName)) {
                    hasCategoryId = true;
                }
            }
            if (!hasCategoryId) {
                try (PreparedStatement alter = conn.prepareStatement("ALTER TABLE recipe ADD COLUMN category_id INTEGER")) {
                    alter.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not ensure category_id column: " + e.getMessage());
        }
    }
    
    public static void categorydB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();
        
        
        ensureCategoryColumn();
        
        int recipeId;
        if (recipe.lastInsertedRecipeId > 0) {
            recipeId = recipe.lastInsertedRecipeId;
            System.out.println("\nLinking category to your new recipe (ID: " + recipeId + ").");
        } else {
            int lastOwnedId = -1;
            String sqlLatest = "SELECT COALESCE(MAX(r.r_ID), -1) AS last_id FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = ?";
            try (Connection conn = config.connectDB(); PreparedStatement ps = conn.prepareStatement(sqlLatest)) {
                ps.setInt(1, User.currentUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        lastOwnedId = rs.getInt("last_id");
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Error selecting latest recipe: " + e.getMessage());
                return;
            }
            if (lastOwnedId <= 0) {
                System.out.println("⚠️ You have no recipes to categorize. Create a recipe first.");
                return;
            }
            recipeId = lastOwnedId;
            System.out.println("\nLinking category to your latest recipe (ID: " + recipeId + ").");
        }
        
        
        int isOwned = db.countRecords(
            "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner " +
            "WHERE r.r_ID = ? AND u.u_ID = ?", 
            recipeId, User.currentUserId
        );
        
        if (isOwned == 0) {
            System.out.println("⚠️ Recipe not found or not owned by you.");
            return;
        }
        
        System.out.print("What category is your recipe: ");
        String name = sc.nextLine();
        System.out.print("Enter description: ");
        String des = sc.nextLine();
        
        
        String sql = "INSERT INTO Category (c_name, c_description) VALUES (?, ?)";
        int categoryId = db.executeAndGetKey(sql, name, des);
        
        if (categoryId > 0) {
            
            String updateSql = "UPDATE recipe SET category_id = ? WHERE r_ID = ?";
            db.updateRecord(updateSql, categoryId, recipeId);
            System.out.println("✅ Category created and linked to recipe successfully!");
            
            recipe.lastInsertedRecipeId = -1;
        } else {
            System.out.println("⚠️ Failed to create category.");
        }
    }
    
    
    public static void categorydBForRecipe(int recipeId){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();
        
        ensureCategoryColumn();
        
        
        int isOwned = db.countRecords(
            "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner " +
            "WHERE r.r_ID = ? AND u.u_ID = ?", 
            recipeId, User.currentUserId
        );
        
        if (isOwned == 0) {
            System.out.println("⚠️ Recipe not found or not owned by you.");
            return;
        }
        
        System.out.print("What category is your recipe: ");
        String name = sc.nextLine();
        System.out.print("Enter description: ");
        String des = sc.nextLine();
        
        String sql = "INSERT INTO Category (c_name, c_description) VALUES (?, ?)";
        int categoryId = db.executeAndGetKey(sql, name, des);
        
        if (categoryId > 0) {
            String updateSql = "UPDATE recipe SET category_id = ? WHERE r_ID = ?";
            db.updateRecord(updateSql, categoryId, recipeId);
            System.out.println("✅ Category created and linked to recipe successfully!");
            recipe.lastInsertedRecipeId = -1;
        } else {
            System.out.println("⚠️ Failed to create category.");
        }
    }
    
    public void discoverRecipes() {
    Scanner sc = new Scanner(System.in);
    config db = new config();

    try (java.sql.Connection conn = config.connectDB()) {

        
        String catSql = "SELECT c_id, c_name FROM category";
        try (java.sql.PreparedStatement catStmt = conn.prepareStatement(catSql);
             java.sql.ResultSet catRs = catStmt.executeQuery()) {

            System.out.println("\n=== CATEGORIES ===");
            while (catRs.next()) {
                System.out.println(catRs.getInt("c_id") + ". " + catRs.getString("c_name"));
            }
        }

        System.out.print("\nEnter a Category ID to explore (0 to cancel): ");
        int catChoice = sc.nextInt();
        sc.nextLine(); // consume newline

        if (catChoice == 0) {
            System.out.println("❌ Discover cancelled.");
            return;
        }

        
        String recipeSql = "SELECT r_ID, r_title, r_description FROM recipe WHERE category_id = ? AND r_status = 'approved'";
        try (java.sql.PreparedStatement recipeStmt = conn.prepareStatement(recipeSql)) {
            recipeStmt.setInt(1, catChoice);
            java.sql.ResultSet recipeRs = recipeStmt.executeQuery();

            System.out.println("\n=== APPROVED RECIPES IN THIS CATEGORY ===");
            boolean hasRecipes = false;
            while (recipeRs.next()) {
                hasRecipes = true;
                System.out.printf("%d. %s - %s%n",
                        recipeRs.getInt("r_ID"),
                        recipeRs.getString("r_title"),
                        recipeRs.getString("r_description"));
            }

            if (!hasRecipes) {
                System.out.println("⚠️ No approved recipes found for this category.");
                return;
            }
        }

        System.out.print("\nEnter Recipe ID to view details (0 to go back): ");
        int recipeId = sc.nextInt();
        sc.nextLine();

        if (recipeId == 0) {
            System.out.println("Returning to categories...");
            return;
        }

        
        String detailSql = "SELECT r_title, r_description, r_instruction, r_date " +
                           "FROM recipe WHERE r_ID = ? AND r_status = 'approved'";
        try (java.sql.PreparedStatement detailStmt = conn.prepareStatement(detailSql)) {
            detailStmt.setInt(1, recipeId);
            java.sql.ResultSet detailRs = detailStmt.executeQuery();

            if (detailRs.next()) {
                System.out.println("\n=== RECIPE DETAILS ===");
                System.out.println("Title       : " + detailRs.getString("r_title"));
                System.out.println("Description : " + detailRs.getString("r_description"));
                System.out.println("Instructions: " + detailRs.getString("r_instruction"));
                System.out.println("Date        : " + detailRs.getString("r_date"));
            } else {
                System.out.println("⚠️ Recipe not found or not approved.");
            }
        }

    } catch (Exception e) {
        System.out.println("⚠️ Error in Discover Recipes: " + e.getMessage());
    }
}

}
