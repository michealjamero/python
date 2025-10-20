/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import config.config;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class recipe {
    public static int lastInsertedRecipeId = -1;
    public static void recipeDB(){
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();

        // Ensure ownership column exists before inserting
        ensureOwnerColumn();
        String owner = User.currentUsername != null ? User.currentUsername : "";
        if (owner.isEmpty()) {
            System.out.println("⚠️ No logged-in user detected. Please login first.");
            return;
        }
        
        System.out.print("Enter Title: ");
        String title = sc.nextLine(); 

        System.out.print("Enter description: ");
        String des = sc.nextLine();  

        System.out.print("Enter instructions: ");
        String ins = sc.nextLine();   

        System.out.print("Enter date Created (YYYYMMDD): ");
        String date = sc.nextLine();   
                
        String sql = "INSERT INTO recipe (r_title, r_description, r_instruction, r_date, r_owner) VALUES (?, ?, ?, ?, ?)";
        int newId = db.executeAndGetKey(sql, title, des, ins, date, owner);
        if (newId > 0) {
            System.out.println("Record added successfully! New Recipe ID: " + newId);
            lastInsertedRecipeId = newId;
        } else {
            System.out.println("⚠️ Failed to create recipe.");
            lastInsertedRecipeId = -1;
        }
    }

    private static void ensureOwnerColumn() {
        try (Connection conn = config.connectDB();
             PreparedStatement check = conn.prepareStatement("PRAGMA table_info(recipe)");
             ResultSet rs = check.executeQuery()) {
            boolean hasOwner = false;
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("r_owner".equalsIgnoreCase(colName)) {
                    hasOwner = true;
                }
            }
            if (!hasOwner) {
                try (PreparedStatement alter = conn.prepareStatement("ALTER TABLE recipe ADD COLUMN r_owner TEXT")) {
                    alter.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not ensure owner column: " + e.getMessage());
        }
    }
}
