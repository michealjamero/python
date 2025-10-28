
package main;

import config.config;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class update {
    public static void updateDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        User user = new User();
        
        
        System.out.println("\n=== UPDATE MENU ===");
        System.out.println("1. Update a recipe");
        System.out.println("2. Update an ingredient");
        System.out.println("3. Update a category");
        System.out.println("4. Update all");
        System.out.println("0. BACK");
        System.out.print("Choose to update: ");
        int choose = sc.nextInt();
        sc.nextLine(); 
        
        switch(choose){
            case 0:
                System.out.println("Returning to previous menu...");
                return;
            case 1:
                
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
                
                {
                    String countSqlIng = "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                    int ownedCountIng = db.countRecords(countSqlIng);
                    if (ownedCountIng == 0) {
                        System.out.println("You have no recipes.");
                        return;
                    }
                    System.out.println("\nYour Recipes:");
                    String listSqlIng = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                                        "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                    String[] headersIng = {"ID","TITLE","DESCRIPTION","DATE","STATUS"};
                    String[] columnsIng = {"r_ID","r_title","r_description","r_date","r_status"};
                    db.viewRecords(listSqlIng, headersIng, columnsIng);

                    System.out.print("\nEnter recipe ID whose ingredients you want to update (0 to cancel): ");
                    int recipeIdForIng = sc.nextInt();
                    sc.nextLine();
                    if (recipeIdForIng == 0) {
                        System.out.println("Update cancelled.");
                        return;
                    }

                    
                    int isOwnedIng = db.countRecords(
                        "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE r.r_ID = ? AND u.u_ID = ?",
                        recipeIdForIng, User.currentUserId
                    );
                    if (isOwnedIng == 0) {
                        System.out.println("You can only update ingredients of your own recipes.");
                        return;
                    }

                    
                    int ingCount = db.countRecords("SELECT COUNT(*) FROM ingredient WHERE i_recipeID = ?", recipeIdForIng);
                    System.out.println("\n=== INGREDIENTS FOR RECIPE ID: " + recipeIdForIng + " ===");
                    String ingListSql = "SELECT i_id, i_name, i_quantity, i_unit FROM ingredient WHERE i_recipeID = " + recipeIdForIng;
                    String[] ingHeaders = {"ID","NAME","QUANTITY","UNIT"};
                    String[] ingColumns = {"i_id","i_name","i_quantity","i_unit"};
                    db.viewRecords(ingListSql, ingHeaders, ingColumns);
                    if (ingCount == 0) {
                        System.out.println("No ingredients found for this recipe.");
                        return;
                    }

                    if (ingCount == 1) {
                        
                        try (Connection conn = config.connectDB();
                             PreparedStatement pst = conn.prepareStatement("SELECT i_id, i_name, i_quantity, i_unit FROM ingredient WHERE i_recipeID = ? LIMIT 1")) {
                            pst.setInt(1, recipeIdForIng);
                            try (ResultSet rs = pst.executeQuery()) {
                                if (rs.next()) {
                                    int idIngredient = rs.getInt("i_id");
                                    String curName = rs.getString("i_name");
                                    String curQty = rs.getString("i_quantity");
                                    String curUnit = rs.getString("i_unit");
                                    System.out.println("\nUpdating the only ingredient: ID " + idIngredient + " (" + curName + ", qty: " + curQty + ", unit: " + curUnit + ")");

                                    System.out.print("Enter ingredient name: ");
                                    String ingName = sc.nextLine();
                                    System.out.print("Enter quantity: ");
                                    String qty = sc.nextLine();
                                    System.out.print("Enter unit: ");
                                    String unit = sc.nextLine();

                                    String sqlUpdateIn = "UPDATE ingredient SET i_name = ?, i_quantity = ?, i_unit = ? WHERE i_id = ?";
                                    db.updateRecord(sqlUpdateIn, ingName, qty, unit, idIngredient);
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("⚠️ Error updating ingredient: " + e.getMessage());
                        }
                    } else {
                        
                        System.out.println("\nUpdate Options:");
                        System.out.println("1. Update a specific ingredient");
                        System.out.println("2. Update all ingredients in this recipe");
                        System.out.print("Choose an option (1-2): ");
                        int ingOption = sc.nextInt();
                        sc.nextLine();

                        if (ingOption == 1) {
                            
                            System.out.print("Enter ingredient ID to update (0 to cancel): ");
                            int idIngredient = sc.nextInt();
                            sc.nextLine();
                            if (idIngredient == 0) {
                                System.out.println("Update cancelled.");
                                return;
                            }

                            
                            int belongs = db.countRecords("SELECT COUNT(*) FROM ingredient WHERE i_id = ? AND i_recipeID = ?", idIngredient, recipeIdForIng);
                            if (belongs == 0) {
                                System.out.println("⚠️ Ingredient not found for this recipe.");
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
                        } else if (ingOption == 2) {
                            
                            try (Connection conn = config.connectDB();
                                 PreparedStatement pst = conn.prepareStatement("SELECT i_id, i_name, i_quantity, i_unit FROM ingredient WHERE i_recipeID = ?");) {
                                pst.setInt(1, recipeIdForIng);
                                try (ResultSet rs = pst.executeQuery()) {
                                    while (rs.next()) {
                                        int ingId = rs.getInt("i_id");
                                        String curName = rs.getString("i_name");
                                        String curQty = rs.getString("i_quantity");
                                        String curUnit = rs.getString("i_unit");
                                        System.out.println("\nIngredient ID: " + ingId + " (" + curName + ", qty: " + curQty + ", unit: " + curUnit + ")");
                                        System.out.print("Enter new name: ");
                                        String newName = sc.nextLine();
                                        System.out.print("Enter new quantity: ");
                                        String newQty = sc.nextLine();
                                        System.out.print("Enter new unit: ");
                                        String newUnit = sc.nextLine();

                                        String sqlUpdateAll = "UPDATE ingredient SET i_name = ?, i_quantity = ?, i_unit = ? WHERE i_id = ?";
                                        db.updateRecord(sqlUpdateAll, newName, newQty, newUnit, ingId);
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("⚠️ Error updating all ingredients: " + e.getMessage());
                            }
                        } else {
                            System.out.println("Invalid option.");
                        }
                    }
                }
                break;
            case 3:
                
                {
                    String countSqlCat = "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                    int ownedCountCat = db.countRecords(countSqlCat);
                    if (ownedCountCat == 0) {
                        System.out.println("You have no recipes to update category.");
                        return;
                    }

                    System.out.println("\nYour Recipes:");
                    String listSqlCat = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                                        "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
                    String[] headersCat = {"ID","TITLE","DESCRIPTION","DATE","STATUS"};
                    String[] columnsCat = {"r_ID","r_title","r_description","r_date","r_status"};
                    db.viewRecords(listSqlCat, headersCat, columnsCat);

                    System.out.print("\nEnter recipe ID to update its category (0 to cancel): ");
                    int recipeIdForCat = sc.nextInt();
                    sc.nextLine();
                    if (recipeIdForCat == 0) {
                        System.out.println("Update cancelled.");
                        return;
                    }

                    
                    int isOwnedCat = db.countRecords(
                        "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE r.r_ID = ? AND u.u_ID = ?",
                        recipeIdForCat, User.currentUserId
                    );
                    if (isOwnedCat == 0) {
                        System.out.println("You can only update categories of your own recipes.");
                        return;
                    }

                    Integer currentCategoryId = null;
                    try (Connection conn = config.connectDB();
                         PreparedStatement pst = conn.prepareStatement("SELECT category_id FROM recipe WHERE r_ID = ?")) {
                        pst.setInt(1, recipeIdForCat);
                        try (ResultSet rs = pst.executeQuery()) {
                            if (rs.next()) {
                                Object catObj = rs.getObject("category_id");
                                if (catObj != null) {
                                    currentCategoryId = (rs.getInt("category_id"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("⚠️ Error checking current category: " + e.getMessage());
                    }

                    if (currentCategoryId == null || currentCategoryId <= 0) {
                        System.out.println("No category linked to this recipe. Create one:");
                        System.out.print("Enter category name: ");
                        String newCatName = sc.nextLine();
                        System.out.print("Enter description: ");
                        String newCatDesc = sc.nextLine();

                        int newCatId = db.executeAndGetKey("INSERT INTO category (c_name, c_description) VALUES (?, ?)", newCatName, newCatDesc);
                        if (newCatId > 0) {
                            db.updateRecord("UPDATE recipe SET category_id = ? WHERE r_ID = ?", newCatId, recipeIdForCat);
                            System.out.println("✅ Category created and linked to recipe.");
                        } else {
                            System.out.println("⚠️ Failed to create category.");
                        }
                    } else {
                        try (Connection conn = config.connectDB();
                             PreparedStatement pst = conn.prepareStatement("SELECT c_name, c_description FROM category WHERE c_id = ?")) {
                            pst.setInt(1, currentCategoryId);
                            try (ResultSet rs = pst.executeQuery()) {
                                if (rs.next()) {
                                    System.out.println("Current Category: " + rs.getString("c_name") + " - " + rs.getString("c_description"));
                                }
                            }
                        } catch (Exception e) {
                           
                        }

                        System.out.print("Enter new category name: ");
                        String categoryName = sc.nextLine();
                        System.out.print("Enter new description: ");
                        String desc = sc.nextLine();

                        String sqlUpdateCate = "UPDATE category SET c_name = ?, c_description = ? WHERE c_id = ?";
                        db.updateRecord(sqlUpdateCate, categoryName, desc, currentCategoryId);
                    }
                }
                break;
            case 4:
                System.out.println("\n=== UPDATE ALL ===");
                System.out.println("This will allow you to update recipes, ingredients, and categories sequentially.");
                
                
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
                        isOwned = db.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE r.r_ID = ? AND u.u_ID = ?", recipeId, User.currentUserId);
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