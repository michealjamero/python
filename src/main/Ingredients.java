package main;

import config.config;
import java.util.Scanner;

public class Ingredients {
    public static void ingredientsDB() {
        Scanner sc = new Scanner(System.in);
        config db = new config();
        db.connectDB();

        // Select target recipe to attach ingredients
        System.out.println("\n=== SELECT RECIPE TO ADD INGREDIENTS ===");
        viewRecipe.viewRecordDB();
        System.out.print("\nEnter Recipe ID: ");
        int recipeId = sc.nextInt();
        sc.nextLine(); // consume newline

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

            // Insert ingredient bound to the selected recipe
            String sql = "INSERT INTO Ingredient (i_recipeID, i_name, i_quantity, i_unit) VALUES (?, ?, ?, ?)";
            db.addRecord(sql, recipeId, name, quantity, unit);

            // Verify insertion before printing success
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
