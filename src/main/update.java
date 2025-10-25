/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import config.config;
import java.util.Scanner;

/**
 *
 * @author PC 30
 */
public class update {
    public static void updateDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();
        
        System.out.println("\n=== UPDATE MENU ===");
        System.out.println("1. Update a recipe");
        System.out.println("2. Update an ingredient");
        System.out.println("3. Update a category");
        System.out.println("4. Update all");
        System.out.println("0. BACK");
        System.out.print("Choose to update: ");
        int choose = sc.nextInt();
        sc.nextLine(); // consume newline
        
        switch(choose){
            case 0:
                System.out.println("Returning to previous menu...");
                return;
            case 1:
                // Show the current user's recipes before asking for ID
                String countSql = "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                int ownedCount = db.countRecords(countSql);
                if (ownedCount == 0) {
                    System.out.println("You have no recipes to update.");
                    return;
                }
                System.out.println("\nYour Recipes:");
                String listSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_instruction, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                                  "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                String[] headers = {"ID","TITLE","DESCRIPTION","INSTRUCTION","DATE","STATUS"};
                String[] columns = {"r_ID","r_title","r_description","r_instruction","r_date","r_status"};
                db.viewRecords(listSql, headers, columns);
                
                System.out.print("\nEnter recipe ID to update (0 to cancel): ");
                int id = sc.nextInt();
                sc.nextLine();
                if (id == 0) {
                    System.out.println("Update cancelled.");
                    return;
                }
                
                // Optional ownership check before allowing update
                int isOwned = db.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE r.r_ID = ? AND u.u_ID = ?", id, User.currentUserId);
                if (isOwned == 0) {
                    System.out.println("You can only update your own recipes.");
                    return;
                }
                
                System.out.print("Enter new title: ");
                String title = sc.nextLine();
                System.out.print("Enter new description: ");
                String description = sc.nextLine();
                System.out.print("Enter new instruction: ");
                String instruction = sc.nextLine();
                
                String sqlUpdate = "UPDATE recipe SET r_title = ?, r_description = ?, r_instruction = ? WHERE r_id = ?";
                db.updateRecord(sqlUpdate, title, description, instruction, id);
                break;
            case 2:
                System.out.print("Enter ingredient ID to update (0 to cancel): ");
                int idIngredient = sc.nextInt();
                sc.nextLine();
                if (idIngredient == 0) {
                    System.out.println("Update cancelled.");
                    return;
                }
                
                System.out.print("Enter ingredient name: ");
                String ingName = sc.nextLine();
                System.out.print("Enter quantity: ");
                String qty = sc.nextLine();
                System.out.print("Enter unit: ");
                String unit = sc.nextLine();
                
                String sqlUpdateIn = "UPDATE ingredient SET i_name = ?, i_quantity = ?, i_unit = ? WHERE i_id = ?";
                db.updateRecord(sqlUpdateIn, ingName, qty, unit, idIngredient);
                break;
            case 3:
                System.out.print("Enter category ID to update (0 to cancel): ");
                int idCategory = sc.nextInt();
                sc.nextLine();
                if (idCategory == 0) {
                    System.out.println("Update cancelled.");
                    return;
                }
                
                System.out.print("Enter category name: ");
                String categoryName = sc.nextLine();
                System.out.print("Enter description: ");
                String desc = sc.nextLine();
                
                String sqlUpdateCate = "UPDATE category SET c_name = ?, c_description = ? WHERE c_id = ?";
                db.updateRecord(sqlUpdateCate, categoryName, desc, idCategory);
                break;
            case 4:
                System.out.println("\n=== UPDATE ALL ===");
                System.out.println("This will allow you to update recipes, ingredients, and categories sequentially.");
                
                // Update recipes
                System.out.println("\n--- UPDATING RECIPES ---");
                String countRecipeSql = "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                int recipeCount = db.countRecords(countRecipeSql);
                if (recipeCount > 0) {
                    System.out.println("\nYour Recipes:");
                    String listRecipeSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_instruction, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                                          "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                    String[] recipeHeaders = {"ID","TITLE","DESCRIPTION","INSTRUCTION","DATE","STATUS"};
                    String[] recipeColumns = {"r_ID","r_title","r_description","r_instruction","r_date","r_status"};
                    db.viewRecords(listRecipeSql, recipeHeaders, recipeColumns);
                    
                    System.out.print("\nEnter recipe ID to update (0 to skip): ");
                    int recipeId = sc.nextInt();
                    sc.nextLine();
                    if (recipeId != 0) {
                        int isOwned = db.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE r.r_ID = ? AND u.u_ID = ?", recipeId, User.currentUserId);
                        if (isOwned > 0) {
                            System.out.print("Enter new title: ");
                            String newTitle = sc.nextLine();
                            System.out.print("Enter new description: ");
                            String newDescription = sc.nextLine();
                            System.out.print("Enter new instruction: ");
                            String newInstruction = sc.nextLine();
                            
                            String sqlUpdateRecipe = "UPDATE recipe SET r_title = ?, r_description = ?, r_instruction = ? WHERE r_id = ?";
                            db.updateRecord(sqlUpdateRecipe, newTitle, newDescription, newInstruction, recipeId);
                        } else {
                            System.out.println("You can only update your own recipes.");
                        }
                    }
                } else {
                    System.out.println("You have no recipes to update.");
                }
                
                // Update ingredients
                System.out.println("\n--- UPDATING INGREDIENTS ---");
                System.out.println("Available ingredients:");
                String ingredientListSql = "SELECT i_id, i_name, i_quantity, i_unit FROM ingredient";
                String[] ingredientHeaders = {"ID","NAME","QUANTITY","UNIT"};
                String[] ingredientColumns = {"i_id","i_name","i_quantity","i_unit"};
                db.viewRecords(ingredientListSql, ingredientHeaders, ingredientColumns);
                
                System.out.print("\nEnter ingredient ID to update (0 to skip): ");
                int ingredientId = sc.nextInt();
                sc.nextLine();
                if (ingredientId != 0) {
                    System.out.print("Enter ingredient name: ");
                    String newIngName = sc.nextLine();
                    System.out.print("Enter quantity: ");
                    String newQty = sc.nextLine();
                    System.out.print("Enter unit: ");
                    String newUnit = sc.nextLine();
                    
                    String sqlUpdateIngredient = "UPDATE ingredient SET i_name = ?, i_quantity = ?, i_unit = ? WHERE i_id = ?";
                    db.updateRecord(sqlUpdateIngredient, newIngName, newQty, newUnit, ingredientId);
                }
                
                // Update categories
                System.out.println("\n--- UPDATING CATEGORIES ---");
                System.out.println("Available categories:");
                String categoryListSql = "SELECT c_id, c_name, c_description FROM category";
                String[] categoryHeaders = {"ID","NAME","DESCRIPTION"};
                String[] categoryColumns = {"c_id","c_name","c_description"};
                db.viewRecords(categoryListSql, categoryHeaders, categoryColumns);
                
                System.out.print("\nEnter category ID to update (0 to skip): ");
                int categoryId = sc.nextInt();
                sc.nextLine();
                if (categoryId != 0) {
                    System.out.print("Enter category name: ");
                    String newCategoryName = sc.nextLine();
                    System.out.print("Enter description: ");
                    String newDesc = sc.nextLine();
                    
                    String sqlUpdateCategory = "UPDATE category SET c_name = ?, c_description = ? WHERE c_id = ?";
                    db.updateRecord(sqlUpdateCategory, newCategoryName, newDesc, categoryId);
                }
                
                System.out.println("\nUpdate all process completed!");
                break;
            default:
                System.out.println("Invalid choice.");
        }
    }
}

public void updateRecipe(int userId) {
    // Bind user id to shared state, then delegate to existing flow
    User.currentUserId = userId;
    updateDB();
}
