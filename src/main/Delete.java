package main;

import config.config;
import java.util.Scanner;

/**
 *
 * @author PC 30
 */
public class Delete {
    public static void deleteDB(){
        Scanner sc = new Scanner(System.in);
        config dbConfig = new config();
        dbConfig.connectDB();
        
        // Show the current user's recipes first
        String countSql = "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
        int ownedCount = dbConfig.countRecords(countSql);
        if (ownedCount == 0) {
            System.out.println("You have no recipes to delete.");
            return;
        }
        System.out.println("\nYour Recipes:");
        String listSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_date, COALESCE(r.r_status,'Draft') AS r_status " +
                         "FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE u.u_ID = " + User.currentUserId;
        String[] headers = {"ID","TITLE","DESCRIPTION","DATE","STATUS"};
        String[] columns = {"r_ID","r_title","r_description","r_date","r_status"};
        dbConfig.viewRecords(listSql, headers, columns);
        
        System.out.println("\n=== DELETE RECIPE ===");
        System.out.print("Enter Recipe ID to delete (0 to cancel): ");
        int id = sc.nextInt();
        sc.nextLine(); // consume newline
        
        if (id == 0) {
            System.out.println("Delete cancelled. Returning to menu...");
            return;
        }
        
        // Ownership check
        int isOwned = dbConfig.countRecords("SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner WHERE r.r_ID = ? AND u.u_ID = ?", id, User.currentUserId);
        if (isOwned == 0) {
            System.out.println("You can only delete your own recipes.");
            return;
        }
        
        String sqlDelete = "DELETE FROM recipe WHERE r_id = ?";
        dbConfig.deleteRecord(sqlDelete, id);
    }
}
