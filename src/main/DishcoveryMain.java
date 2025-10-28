package main;
 
import config.config; 
import java.util.Scanner; 
 
public class DishcoveryMain { 
 
    public static void main(String[] args) { 
        config con = new config(); 
        con.connectDB(); 
        Scanner sc = new Scanner(System.in); 
 
         
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
            int choice = readInt(sc, "Enter choice: ", 1, 3); 
 
            switch (choice) { 
                case 1: 
                    String em = readEmail(sc, "Enter email: "); 
                    String pas = readNonEmpty(sc, "Enter Password: "); 
                    String hashed = config.hashPassword(pas);

                    while (true) { 
                        String qry = "SELECT u_ID, u_username, COALESCE(u_verified,'Active') AS u_status, COALESCE(u_role,'user') AS u_role FROM Users WHERE u_email = ? AND u_pass = ?"; 
                        java.util.List<java.util.Map<String, Object>> result = con.fetchRecords(qry, em, hashed); 
 
                        if (result.isEmpty()) { 
                            System.out.println("❌ INVALID CREDENTIALS"); 
                            break; 
                        } else { 
                            java.util.Map<String, Object> user = result.get(0); 
                            String status = String.valueOf(user.get("u_status")); 
                            String role = String.valueOf(user.get("u_role")); 
 
                             
                            try { 
                                User.currentUserId = Integer.parseInt(String.valueOf(user.get("u_ID"))); 
                            } catch (Exception e) { 
                                User.currentUserId = -1; 
                            } 
                            User.currentUsername = String.valueOf(user.get("u_username")); 
 
                            if ("admin".equals(role)) { 
                                 
                                while (true) { 
                                    System.out.println("\n=== ADMIN PANEL ==="); 
                                    System.out.println("(1) View All Recipes"); 
                                    System.out.println("2.Review Pending Recipesh"); 
                                    System.out.println("(3.Approve Admins"); 
                                    System.out.println("0.Logout"); 
                                    int adminChoice = readInt(sc, "Enter choice: ", 0, 3); 
 
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
                                            User.viewMyRecipesTable();
                                            break; 
 
                                        case 4: 
                                            del.deleteDB(); 
                                            break; 
                                        case 5: 
                                            System.out.println("\n=== Recipe Options ==="); 
                                            System.out.println("1. Share a Recipe"); 
                                            System.out.println("2. Discover Recipes"); 
                                            System.out.println("3. Back"); 
                                            int subChoice = readInt(sc, "Choose an option: ", 1, 3); 
 
                                            if (subChoice == 1) { 
                                                userObj.shareRecipe(); 
                                            } else if (subChoice == 2) { 
                                                userObj.viewApprovedRecipes(); 
                                            } else if (subChoice == 3) { 
                                              
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
 
                            
                            break; 
                        } 
                    } 
                    break; 
                case 2: 
                    System.out.println("=== USER REGISTRATION ==="); 
                    String regFullName = readNonEmpty(sc, "Enter Full Name: ");
                    String regUsername = readNonEmpty(sc, "Enter Username: ");
                    String regEmail = readEmail(sc, "Enter Email: ");
                    String regPass = readNonEmpty(sc, "Enter Password: ");
                    String regType = readNonEmpty(sc, "Enter User Type (admin/user): ");
                    String regHashed = config.hashPassword(regPass);

                    String insertSql = "INSERT INTO Users(u_full_name, u_email, u_username, u_pass, u_role) VALUES(?, ?, ?, ?, ?)";
                    con.addRecord(insertSql, regFullName, regEmail, regUsername, regHashed, regType);
                    System.out.println("User registered successfully!");
                    break; 
                case 3: 
                    System.out.println("👋 Thank you for using Dishcovery System!"); 
                    return; 
                default: 
                    System.out.println("⚠️ Invalid option. Please try again."); 
            } 
             
            cont = readYesNo(sc, "Continue? (y/n): "); 
        } while (cont == 'y' || cont == 'Y'); 
         
        System.out.println("👋 Thank you for using Dishcovery System!"); 
    } 
 
    private static int readInt(Scanner sc, String prompt, int min, int max) { 
        while (true) { 
            System.out.print(prompt); 
            String line = sc.nextLine().trim(); 
            try { 
                int val = Integer.parseInt(line); 
                if (val < min || val > max) { 
                    System.out.println("⚠️ Please enter a number between " + min + " and " + max + "."); 
                } else { 
                    return val; 
                } 
            } catch (NumberFormatException e) { 
                System.out.println("⚠️ Invalid number. Please try again."); 
            } 
        } 
    } 
 
    private static String readNonEmpty(Scanner sc, String prompt) { 
        while (true) { 
            System.out.print(prompt); 
            String line = sc.nextLine().trim(); 
            if (!line.isEmpty()) { 
                return line; 
            } 
            System.out.println("⚠️ Input cannot be empty. Please try again."); 
        } 
    } 
 
    private static String readEmail(Scanner sc, String prompt) { 
        while (true) { 
            System.out.print(prompt); 
            String email = sc.nextLine().trim(); 
            if (isValidEmail(email)) { 
                return email; 
            } 
            System.out.println("⚠️ Invalid email format. Example: user@example.com"); 
        } 
    } 
 
    private static boolean isValidEmail(String email) { 
        
        return email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"); 
    } 
 
    private static char readYesNo(Scanner sc, String prompt) { 
        while (true) { 
            System.out.print(prompt); 
            String line = sc.nextLine().trim().toLowerCase(); 
            if (line.equals("y") || line.equals("yes")) { 
                return 'y'; 
            } else if (line.equals("n") || line.equals("no")) { 
                return 'n'; 
            } 
            System.out.println("⚠️ Please answer with 'y' or 'n'."); 
        } 
    } 
}
