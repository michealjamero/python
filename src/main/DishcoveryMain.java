package main;
 
import config.config; 
import java.util.Scanner; 
 
public class DishcoveryMain { 
 
    public static void main(String[] args) { 
        config con = new config(); 
        con.connectDB(); 
        Scanner sc = new Scanner(System.in); 
 
        // Feature objects 
        recipe rc = new recipe(); 
        Ingredients ind = new Ingredients(); 
        category cat = new category(); 
        viewRecipe view = new viewRecipe(); 
        update up = new update(); 
        Delete del = new Delete(); 
 
        char cont; 
        do { 
            System.out.println("===== MAIN MENU ====="); 
            System.out.println("1. Login"); 
            System.out.println("2. Register"); 
            System.out.println("3. Exit"); 
            System.out.print("Enter choice: "); 
            int choice = sc.nextInt(); 
            sc.nextLine(); // clear buffer 
 
            switch (choice) { 
                case 1: 
                    System.out.print("Enter email: "); 
                    String em = sc.next(); 
                    System.out.print("Enter Password: "); 
                    String pas = sc.next(); 
                    sc.nextLine(); // clear buffer 
 
                    while (true) { 
                        String qry = "SELECT u_ID, u_username, COALESCE(u_verified,'Active') AS u_status, COALESCE(u_role,'user') AS u_role FROM Users WHERE u_email = ? AND u_pass = ?"; 
                        java.util.List<java.util.Map<String, Object>> result = con.fetchRecords(qry, em, pas); 
 
                        if (result.isEmpty()) { 
                            System.out.println("❌ INVALID CREDENTIALS"); 
                            break; 
                        } else { 
                            java.util.Map<String, Object> user = result.get(0); 
                            String status = String.valueOf(user.get("u_status")); 
                            String role = String.valueOf(user.get("u_role")); 
 
                            // Persist logged-in user 
                            try { 
                                User.currentUserId = Integer.parseInt(String.valueOf(user.get("u_ID"))); 
                            } catch (Exception e) { 
                                User.currentUserId = -1; 
                            } 
                            User.currentUsername = String.valueOf(user.get("u_username")); 
 
                            if ("admin".equals(role)) { 
                                // Admin Panel Loop 
                                while (true) { 
                                    System.out.println("\n=== ADMIN PANEL ==="); 
                                    System.out.println("(1) View All Recipes"); 
                                    System.out.println("2.Review Pending Recipesh"); 
                                    System.out.println("(3.Approve Admins"); 
                                    System.out.println("0.Logout"); 
                                    System.out.print("Enter choice: "); 
                                    int adminChoice = sc.nextInt(); 
                                    sc.nextLine(); // clear buffer 
 
                                    switch (adminChoice) { 
                                        case 1: 
                                            view.viewRecordDB(); 
                                            break; 
                                        case 2: 
                                            Admin admin = new Admin();
                                            admin.reviewRecipes(); 
                                             break; 
 
                                        case 3: 
                                            System.out.println("\n=== Admin Account Approval ==="); 
                                            User.approveAdmins(); 
                                            break; 
                                        case 0: 
                                            System.out.println("👋 Admin logged out."); 
                                            break; 
                                        default: 
                                            System.out.println("⚠️ Invalid option."); 
                                    } 
 
                                    if (adminChoice == 0) { 
                                        break; 
                                    } 
                                } 
                            } else { 
                                // User Panel Loop 
                                while (true) { 
                                    User userObj = new User(); 
                                    System.out.println("\n=== WELCOME TO Dishcovery ==="); 
                                    System.out.println("(1) Add Recipe"); 
                                    System.out.println("(2) Update Recipe"); 
                                    System.out.println("(3) View Recipe"); 
                                    System.out.println("(4) Delete Recipe"); 
                                    System.out.println("(5) Share Recipe"); 
                                    System.out.println("(6) View Profile"); 
                                    System.out.println("(0) Logout"); 
                                    System.out.print("Enter choice: "); 
                                    int userChoice = sc.nextInt(); 
                                    sc.nextLine(); // clear buffer 
 
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
                                            viewRecipe.viewRecordDB(); // show list first 
                                            System.out.print("\nEnter Recipe ID to view details (0 to cancel): "); 
                                            int recipeId = sc.nextInt(); 
                                            sc.nextLine(); 
 
                                            if (recipeId != 0) { 
                                                view.viewAllRecipe(); 
                                            } else { 
                                                System.out.println("Returning to main menu..."); 
                                            } 
                                            break; 
 
                                        case 4: 
                                            del.deleteDB(); 
                                            break; 
                                        case 5: 
                                            System.out.println("\n=== Recipe Options ==="); 
                                            System.out.println("1. Share a Recipe"); 
                                            System.out.println("2. Discover Recipes"); 
                                            System.out.println("3. Back"); 
                                            System.out.print("Choose an option: "); 
                                            int subChoice = sc.nextInt(); 
                                            sc.nextLine(); 
 
                                            if (subChoice == 1) { 
                                                userObj.shareRecipe(); 
                                            } else if (subChoice == 2) { 
                                                userObj.viewApprovedRecipes(); 
                                            } else if (subChoice == 3) { 
                                                // back to main 
                                            } else { 
                                                System.out.println("Invalid choice. Returning to main menu..."); 
                                            } 
                                            break; 
                                        case 6: 
                                            userObj.viewProfile(); 
                                            break; 
                                        case 0: 
                                            System.out.println("👋 Logged out."); 
                                            break; 
                                        default: 
                                            System.out.println("⚠️ Invalid option"); 
                                    } 
 
                                    if (userChoice == 0) { 
                                        break; 
                                    } 
                                } 
                            } 
 
                            // exit login loop after panel interaction 
                            break; 
                        } 
                    } 
                    break; 
                case 2: 
                    // Registration code 
                    System.out.println("=== USER REGISTRATION ==="); 
                    // Add your registration code 
                    break; 
                case 3: 
                    System.out.println("👋 Thank you for using Dishcovery System!"); 
                    return; 
                default: 
                    System.out.println("⚠️ Invalid option. Please try again."); 
            } 
             
            System.out.print("Continue? (y/n): "); 
            cont = sc.next().charAt(0); 
            sc.nextLine(); 
        } while (cont == 'y' || cont == 'Y'); 
         
        System.out.println("👋 Thank you for using Dishcovery System!"); 
    } 
}
