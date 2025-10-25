/*
 * View functionality for recipes, ingredients, and categories
 */
package main;

import config.config;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Class for viewing recipes, ingredients, and categories
 * @author PC 30
 */
public class viewRecipe {
    
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
    
    /**
     * View all recipes in the database
     */
    public static void viewRecordDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();
        String recipeQuery = "SELECT * FROM recipe";
        String[] recipeHeaders = {"ID","TITLE","DESCRIPTION","INSTRUCTION","DATE"};
        String[] recipeColumns = {"r_ID", "r_title", "r_description","r_instruction","r_date"};
        db.viewRecords(recipeQuery, recipeHeaders, recipeColumns);
    }

    /**
     * View all ingredients in the database
     */
    public static void viewIngredientsDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();
        String ingredientQuery = "SELECT * FROM ingredient";
        String[] ingredientHeaders = {"ID","NAME","QUANTITY","UNIT","RECIPE ID"};
        String[] ingredientColumns = {"i_id", "i_name", "i_quantity", "i_unit", "i_recipeID"};
        db.viewRecords(ingredientQuery, ingredientHeaders, ingredientColumns);
    }

    /**
     * View ingredients for a specific recipe
     */
    public static void viewIngredientsByRecipeDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        
        System.out.println("\n=== View Ingredients by Recipe ===");
        System.out.print("Enter Recipe ID: ");
        int recipeId = sc.nextInt();
        sc.nextLine(); // Consume newline
        
        String ingredientQuery = "SELECT i.i_id, i.i_name, i.i_quantity, i.i_unit, r.r_title " +
                                "FROM ingredient i " +
                                "JOIN recipe r ON i.i_recipeID = r.r_ID " +
                                "WHERE i.i_recipeID = " + recipeId;

        String[] headers = {"ID","NAME","QUANTITY","UNIT","RECIPE TITLE"};
        String[] columns = {"i_id","i_name","i_quantity","i_unit","r_title"};

        int count = db.countRecords("SELECT COUNT(*) FROM ingredient WHERE i_recipeID = ?", recipeId);
        System.out.println("\n=== Ingredients for Recipe ID: " + recipeId + " ===");
        db.viewRecords(ingredientQuery, headers, columns);
        if (count == 0) {
            System.out.println("No ingredients found for Recipe ID: " + recipeId);
        }
    }

    /**
     * View all categories in the database
     */
    public static void viewCategoryDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();
        String categoryQuery = "SELECT * FROM category";
        String[] categoryHeaders = {"ID","NAME","DESCRIPTION"};
        String[] categoryColumns = {"c_id", "c_name", "c_description"};
        db.viewRecords(categoryQuery, categoryHeaders, categoryColumns);
    }

    /**
     * View recipes by category (using recipe.category_id)
     */
    public static void viewRecipesByCategoryDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        
        // Ensure category_id column exists
        ensureCategoryColumn();
        
        System.out.println("\n=== View Recipes by Category ===");
        // First show all categories
        viewCategoryDB();
        
        System.out.print("Enter Category ID: ");
        int categoryId = sc.nextInt();
        sc.nextLine(); // Consume newline
        
        String recipeQuery = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, c.c_name " +
                            "FROM recipe r " +
                            "JOIN category c ON c.c_id = r.category_id " +
                            "WHERE c.c_id = " + categoryId + " " +
                            "GROUP BY r.r_ID";

        String[] headers = {"ID","TITLE","DESCRIPTION","DATE","CATEGORY"};
        String[] columns = {"r_ID","r_title","r_description","r_date","c_name"};

        int count = db.countRecords(
            "SELECT COUNT(*) FROM recipe r JOIN category c ON c.c_id = r.category_id WHERE c.c_id = ?",
            categoryId
        );
        System.out.println("\n=== Recipes in Category ID: " + categoryId + " ===");
        db.viewRecords(recipeQuery, headers, columns);
        if (count == 0) {
            System.out.println("No recipes found for Category ID: " + categoryId);
        }
    }

    public static void viewOwnedRecipeDetails(int recipeId) {
        // Ensure category_id column exists before querying
        ensureCategoryColumn();
    
        String sql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_instruction, r.r_date, COALESCE(r.r_status,'Draft') AS r_status, " +
                     "       c.c_name, c.c_description, " +
                     "       i.i_id, i.i_name, i.i_quantity, i.i_unit " +
                     "FROM recipe r " +
                     "LEFT JOIN category c ON c.c_id = r.category_id " +
                     "LEFT JOIN ingredient i ON i.i_recipeID = r.r_ID " +
                     "JOIN Users u ON u.u_username = r.r_owner " +
                     "WHERE r.r_ID = ? AND u.u_ID = ?";
    
        try (java.sql.Connection conn = config.connectDB();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
    
            pstmt.setInt(1, recipeId);
            pstmt.setInt(2, User.currentUserId);
            java.sql.ResultSet rs = pstmt.executeQuery();
    
            if (!rs.next()) {
                System.out.println("⚠️ No recipe found with ID: " + recipeId + " or you do not own it.");
                return;
            }
    
            System.out.println("\n=== RECIPE DETAILS ===");
            System.out.println("ID          : " + rs.getInt("r_ID"));
            System.out.println("Title       : " + rs.getString("r_title"));
            System.out.println("Status      : " + rs.getString("r_status"));
            System.out.println("Description : " + rs.getString("r_description"));
            System.out.println("Instructions: " + rs.getString("r_instruction"));
            System.out.println("Date        : " + rs.getString("r_date"));
            String catName = rs.getString("c_name");
            String catDesc = rs.getString("c_description");
            System.out.println("Category    : " + (catName == null ? "(none)" : catName));
            if (catDesc != null && !catDesc.isEmpty()) {
                System.out.println("Cat. Desc   : " + catDesc);
            }
    
            System.out.println("\nIngredients:");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.printf("%-5s %-20s %-15s %-10s\n", "ID", "NAME", "QUANTITY", "UNIT");
            System.out.println("--------------------------------------------------------------------------------");
    
            boolean any = false;
            do {
                int ingId = rs.getInt("i_id");
                String ingName = rs.getString("i_name");
                String ingQty = rs.getString("i_quantity");
                String ingUnit = rs.getString("i_unit");
                if (ingName != null) {
                    any = true;
                    System.out.printf("%-5d %-20s %-15s %-10s\n", ingId, ingName,
                                      (ingQty == null ? "" : ingQty), (ingUnit == null ? "" : ingUnit));
                }
            } while (rs.next());
            System.out.println("--------------------------------------------------------------------------------");
            if (!any) {
                System.out.println("No ingredients found.");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error retrieving recipe details: " + e.getMessage());
        }
    }

    public static void viewApprovedRecipeDetails(int recipeId) {
        // Ensure category_id column exists before querying
        ensureCategoryColumn();
    
        String sql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_instruction, r.r_date, r.r_status, " +
                     "       c.c_name, c.c_description, " +
                     "       i.i_id, i.i_name, i.i_quantity, i.i_unit " +
                     "FROM recipe r " +
                     "LEFT JOIN category c ON c.c_id = r.category_id " +
                     "LEFT JOIN ingredient i ON i.i_recipeID = r.r_ID " +
                     "WHERE r.r_ID = ? AND LOWER(r.r_status) = 'approved'";
    
        try (java.sql.Connection conn = config.connectDB();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
    
            pstmt.setInt(1, recipeId);
            java.sql.ResultSet rs = pstmt.executeQuery();
    
            if (!rs.next()) {
                System.out.println("⚠️ No approved recipe found with ID: " + recipeId + ".");
                return;
            }
    
            System.out.println("\n=== RECIPE DETAILS ===");
            System.out.println("ID          : " + rs.getInt("r_ID"));
            System.out.println("Title       : " + rs.getString("r_title"));
            System.out.println("Status      : " + rs.getString("r_status"));
            String catName = rs.getString("c_name");
            String catDesc = rs.getString("c_description");
            System.out.println("Category    : " + (catName == null ? "(none)" : catName));
            if (catDesc != null && !catDesc.isEmpty()) {
                System.out.println("Cat. Desc   : " + catDesc);
            }
            System.out.println("Description : " + rs.getString("r_description"));
            System.out.println("Instructions: " + rs.getString("r_instruction"));
            System.out.println("Date        : " + rs.getString("r_date"));
    
            System.out.println("\nIngredients:");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.printf("%-5s %-20s %-15s %-10s\n", "ID", "NAME", "QUANTITY", "UNIT");
            System.out.println("--------------------------------------------------------------------------------");
    
            boolean any = false;
            do {
                int ingId = rs.getInt("i_id");
                String ingName = rs.getString("i_name");
                String ingQty = rs.getString("i_quantity");
                String ingUnit = rs.getString("i_unit");
                if (ingName != null) {
                    any = true;
                    System.out.printf("%-5d %-20s %-15s %-10s\n", ingId, ingName,
                                      (ingQty == null ? "" : ingQty), (ingUnit == null ? "" : ingUnit));
                }
            } while (rs.next());
            System.out.println("--------------------------------------------------------------------------------");
            if (!any) {
                System.out.println("No ingredients found.");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error retrieving approved recipe details: " + e.getMessage());
        }
    }

    /**
     * View ALL recipes with their ingredients and category
     */
    /**
     * Show all recipes (profile style) directly from allRecipe table
     */
    public static void viewAllRecipe() {
        String sql = "SELECT r_title, r_description, r_instruction, r_date, " +
                     "i_name, i_quantity, i_unit, c_name, c_description " +
                     "FROM allRecipe";

        try (java.sql.Connection conn = config.connectDB();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {

            System.out.println("\n=== ALL RECIPES ===");

            boolean hasResults = false;
            while (rs.next()) {
                hasResults = true;
                System.out.println("----------------------------------------------");
                System.out.println("Title       : " + rs.getString("r_title"));
                System.out.println("Description : " + rs.getString("r_description"));
                System.out.println("Instruction : " + rs.getString("r_instruction"));
                System.out.println("Date        : " + rs.getString("r_date"));
                System.out.println("Ingredient  : " + rs.getString("i_name") +
                                   " (" + rs.getString("i_quantity") + " " + rs.getString("i_unit") + ")");
                System.out.println("Category    : " + rs.getString("c_name") +
                                   " - " + rs.getString("c_description"));
            }

            if (!hasResults) {
                System.out.println("⚠️ No recipes found.");
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error retrieving recipes: " + e.getMessage());
        }
    }

    public void viewAllRecipes() {
        // Delegate to existing static method
        viewAllRecipe();
    }
    
    public void viewMyRecipes(int userId) {
        User.currentUserId = userId;
        config db = new config();
        db.connectDB();
    
        String listSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                         "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + userId;
        String[] headers = {"ID","TITLE","DESCRIPTION","DATE","STATUS"};
        String[] columns = {"r_ID","r_title","r_description","r_date","r_status"};
    
        int count = db.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = ?", userId);
        System.out.println("\n=== MY RECIPES ===");
        db.viewRecords(listSql, headers, columns);
        if (count == 0) {
            System.out.println("You have no recipes.");
        }
    }
}