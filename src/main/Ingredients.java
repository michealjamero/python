package main;

import config.config;
import java.util.Scanner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Ingredients {
    public static void ingredientsDB() {
        config db = new config();
        Scanner sc = new Scanner(System.in);
        db.connectDB();

        int recipeId;
        if (recipe.lastInsertedRecipeId > 0) {
            recipeId = recipe.lastInsertedRecipeId;
            System.out.println("\nAttaching ingredients to your new recipe (ID: " + recipeId + ").");
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
            }
            if (lastOwnedId <= 0) {
                System.out.println("⚠️ You have no recipes to attach ingredients. Create a recipe first.");
                return;
            }
            recipeId = lastOwnedId;
            System.out.println("\nAttaching ingredients to your latest recipe (ID: " + recipeId + ").");
        }

        System.out.print("How many ingredients to add? ");
        int count = sc.nextInt();
        sc.nextLine(); // consume newline

        for (int i = 1; i <= count; i++) {
            System.out.println("\nIngredient " + i + ":");

            System.out.print("Name: ");
            String name = sc.nextLine();

            System.out.print("Quantity: ");
            String quantity = sc.nextLine();

            System.out.print("Unit (e.g., grams, cups, pieces): ");
            String unit = sc.nextLine();

            String sql = "INSERT INTO Ingredient (i_recipeID, i_name, i_quantity, i_unit) VALUES (?, ?, ?, ?)";
            db.addRecord(sql, recipeId, name, quantity, unit);

            int exists = db.countRecords(
                "SELECT COUNT(*) FROM Ingredient WHERE i_recipeID = ? AND i_name = ? AND i_quantity = ? AND i_unit = ?",
                recipeId, name, quantity, unit
            );
            if (exists > 0) {
                System.out.println("Ingredient " + i + " added successfully!");
            } else {
                System.out.println("⚠️ Failed to add Ingredient " + i + ".");
            }
        }
    }
}
