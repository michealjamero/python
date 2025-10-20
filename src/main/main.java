package main;

import config.config;
import java.util.Scanner;

public class main {
    // Add robust integer input reader with bounds validation
    private static int readInt(Scanner sc, String prompt, Integer min, Integer max) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (min != null && val < min) {
                    System.out.println("⚠️ Enter a number >= " + min + ".");
                    continue;
                }
                if (max != null && val > max) {
                    System.out.println("⚠️ Enter a number <= " + max + ".");
                    continue;
                }
                return val;
            } catch (NumberFormatException e) {
                System.out.println("⚠️ Please enter a valid number.");
            }
        }
    }

    public static void main(String[] args) {
        recipe rc = new recipe();
        Ingredients ind = new Ingredients();
        category cat = new category();
        viewRecipe view = new viewRecipe();
        update up = new update();
        Delete del = new Delete();
        Admin Admin = new Admin();

        Scanner sc = new Scanner(System.in);
        String role = null;

        // Authentication Menu Loop
        while (role == null) {
            System.out.println("\n=== AUTH MENU ===");
            System.out.println("(1) Sign Up (User)");
            System.out.println("(2) Login");
            System.out.println("(0) Exit");
            // Use validated integer input
            int choice = readInt(sc, "Choose option: ", 0, 2);

            switch (choice) {
                case 1: 
                    User.registerUser(); 
                    break;
                case 2: 
                    role = User.loginUser(); // role comes from DB (user/admin)
                    break;
                case 0: 
                    System.out.println("👋 Goodbye!"); 
                    return;
                default: 
                    System.out.println("⚠️ Invalid option.");
            }
        }
        if ("admin".equals(role)) {

            while (true) {
                System.out.println("\n=== ADMIN PANEL ===");
                System.out.println("(1) View All Recipes");
                System.out.println("(2) Review Pending Recipes");
                System.out.println("(3) Approve Admins");
                System.out.println("(4) Register Admin");
                System.out.println("(0) Logout");
                int adminChoice = readInt(sc, "Enter choice: ", 0, 4);

                switch (adminChoice) {
                    case 1:
                        view.viewRecordDB();
                        break;
                    case 2:
                        // Gate review to only when there are pending recipes
                        config dbCheck = new config();
                        dbCheck.connectDB();
                        int pendingCount = dbCheck.countRecords("SELECT COUNT(*) FROM recipe WHERE LOWER(r_status) = 'pending'");
                        if (pendingCount == 0) {
                            System.out.println("⚠️ No pending recipes to review. Ask users to share recipes first.");
                        } else {
                            Admin.reviewRecipes();
                        }
                        break;
                    case 3: 
                        User.approveAdmins();
                        break;
                    case 4:
                        Admin.signUpAdminDB();
                        break;
                    case 0:
                        System.out.println("👋 Logged out.");
                        return;
                    default:
                        System.out.println("⚠️ Invalid option");
                }
            }
        } else {
            User user = new User();
            while (true) {
                System.out.println("\n=== WELCOME TO Dishcovery ===");
                System.out.println("(1) Add Recipe");
                System.out.println("(2) Update Recipe");
                System.out.println("(3) View Recipe");
                System.out.println("(4) Delete Recipe");
                System.out.println("(5) Share Recipe");
                System.out.println("(6) View Profile");
                System.out.println("(0) Logout");
                // Validated user menu choice
                int userChoice = readInt(sc, "Enter choice: ", 0, 6);

                switch (userChoice) {
                    case 1: 
                        rc.recipeDB(); 
                        ind.ingredientsDB();
                        cat.categorydB();
                        break;
                    case 2: 
                        up.updateDB(); 
                        break;
                    case 3:
                        // Show only YOUR pending recipes for viewing in this flow
                        config dbList = new config();
                        dbList.connectDB();
                        String listSql = "SELECT r.r_ID, r.r_title, r.r_description, r.r_instruction, r.r_date " +
                                         "FROM recipe r JOIN Users u ON u.u_username = r.r_owner " +
                                         "WHERE LOWER(r.r_status) = 'pending' AND u.u_ID = " + User.currentUserId;
                        String[] headers = {"ID","TITLE","DESCRIPTION","INSTRUCTION","DATE"};
                        String[] columns = {"r_ID","r_title","r_description","r_instruction","r_date"};
                        dbList.viewRecords(listSql, headers, columns);

                        int recipeId = readInt(sc, "\nEnter Recipe ID to view details (0 to cancel): ", 0, null);

                        if (recipeId != 0) {
                            // Enforce that only YOUR pending recipes are viewable here
                            int isPendingAndOwned = dbList.countRecords(
                                "SELECT COUNT(*) FROM recipe r JOIN Users u ON u.u_username = r.r_owner " +
                                "WHERE r.r_ID = ? AND LOWER(r.r_status) = 'pending' AND u.u_ID = ?",
                                recipeId, User.currentUserId
                            );
                            if (isPendingAndOwned == 0) {
                                System.out.println("⚠️ Recipe not found, not pending, or not owned by you.");
                            } else {
                                view.viewOwnedRecipeDetails(recipeId);
                            }
                        } else {
                            System.out.println("Returning to main menu...");
                        }
                        break;

                    case 4: 
                        del.deleteDB(); 
                        break;
                    case 5:
                        while (true) {
                            System.out.println("\n=== Recipe Options ===");
                            System.out.println("1. Share a Recipe");
                            System.out.println("2. Discover Recipes");
                            System.out.println("0. Back");
                            int subChoice = readInt(sc, "Choose an option: ", 0, 2);

                            if (subChoice == 1) {
                                user.shareRecipe();
                            } else if (subChoice == 2) {
                                user.viewApprovedRecipes();
                            } else if (subChoice == 0) {
                                break;
                            } else {
                                System.out.println("Invalid choice.");
                            }
                        }
                        break;
                    case 6: 
                        User.viewProfile(); 
                        break;
                    case 0: 
                        System.out.println("👋 Logged out."); 
                        return;
                    default: 
                        System.out.println("⚠️ Invalid option");
                }
            }
        }
    }
}
