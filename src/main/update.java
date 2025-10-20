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
            default:
                System.out.println("Invalid choice.");
        }
    }
}
