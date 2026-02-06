import dao.*;
import model.*;
import java.sql.Connection;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Connexion à la base de données
        Connection conn = connexion.getConnection();

        if (conn != null) {
            System.out.println("===== TEST D'EXTRACTION DES DONNÉES =====\n");

            // Test 1: Récupérer les 5 premiers utilisateurs
            System.out.println("========================================");
            System.out.println("1. LES 5 PREMIERS UTILISATEURS");
            System.out.println("========================================");
            List<User> users = UserDAO.getUsersLimit(conn, 5);
            for (User user : users) {
                System.out.println(user);
            }
            System.out.println("Nombre d'utilisateurs récupérés: " + users.size() + "\n");

            // Test 2: Récupérer les 5 premières reviews
            System.out.println("========================================");
            System.out.println("2. LES 5 PREMIÈRES REVIEWS");
            System.out.println("========================================");
            List<Review> reviews = ReviewDAO.getReviewsLimit(conn, 5);
            for (Review review : reviews) {
                System.out.println(review);
            }
            System.out.println("Nombre de reviews récupérées: " + reviews.size() + "\n");

            // Test 3: Récupérer les 5 premiers enregistrements Elite
            System.out.println("========================================");
            System.out.println("3. LES 5 PREMIERS ENREGISTREMENTS ELITE");
            System.out.println("========================================");
            List<Elite> elites = EliteDAO.getElitesLimit(conn, 5);
            for (Elite elite : elites) {
                System.out.println(elite);
            }
            System.out.println("Nombre d'enregistrements Elite récupérés: " + elites.size() + "\n");

            // Test 4: Récupérer les 5 premières relations d'amitié
            System.out.println("========================================");
            System.out.println("4. LES 5 PREMIÈRES RELATIONS D'AMITIÉ");
            System.out.println("========================================");
            List<Friend> friends = FriendDAO.getFriendsLimit(conn, 5);
            for (Friend friend : friends) {
                System.out.println(friend);
            }
            System.out.println("Nombre de relations d'amitié récupérées: " + friends.size() + "\n");

            // Test 5: Recherche d'un utilisateur spécifique
            if (!users.isEmpty()) {
                User firstUser = users.get(0);
                System.out.println("========================================");
                System.out.println("5. RECHERCHE D'UN UTILISATEUR PAR ID");
                System.out.println("========================================");
                User foundUser = UserDAO.getUserById(conn, firstUser.getUserId());
                if (foundUser != null) {
                    System.out.println("Utilisateur trouvé: " + foundUser);

                    // Récupérer les reviews de cet utilisateur
                    System.out.println("\nReviews de cet utilisateur:");
                    List<Review> userReviews = ReviewDAO.getReviewsByUserId(conn, foundUser.getUserId());
                    System.out.println("Nombre de reviews: " + userReviews.size());
                    if (!userReviews.isEmpty()) {
                        System.out.println("Première review: " + userReviews.get(0));
                    }

                    // Récupérer les amis de cet utilisateur
                    System.out.println("\nAmis de cet utilisateur:");
                    List<Friend> userFriends = FriendDAO.getFriendsByUserId(conn, foundUser.getUserId());
                    System.out.println("Nombre d'amis: " + userFriends.size());

                    // Récupérer les années elite de cet utilisateur
                    System.out.println("\nAnnées Elite de cet utilisateur:");
                    List<Elite> userElites = EliteDAO.getElitesByUserId(conn, foundUser.getUserId());
                    System.out.println("Nombre d'années elite: " + userElites.size());
                    for (Elite elite : userElites) {
                        System.out.println("  - " + elite);
                    }
                } else {
                    System.out.println("Utilisateur non trouvé!");
                }
            }

            // Test 6: Compter les utilisateurs qui ont au moins une review
            System.out.println("========================================");
            System.out.println("6. NOMBRE D'UTILISATEURS AVEC AU MOINS UNE REVIEW");
            System.out.println("========================================");
            int usersWithReviewsCount = ReviewDAO.getUserReviewCount(conn);
            System.out.println("Nombre d'utilisateurs distincts ayant fait des reviews: " + usersWithReviewsCount + "\n");

            // Test 7: Compter le nombre total d'utilisateurs
            System.out.println("========================================");
            System.out.println("7. NOMBRE TOTAL D'UTILISATEURS");
            System.out.println("========================================");
            int totalUsersCount = UserDAO.getUserCount(conn);
            System.out.println("Nombre total d'utilisateurs: " + totalUsersCount + "\n");

            // Test 8: Compter les utilisateurs sans reviews
            System.out.println("========================================");
            System.out.println("8. NOMBRE D'UTILISATEURS SANS REVIEWS");
            System.out.println("========================================");
            int usersWithoutReviewsCount = UserDAO.getUserCountWithoutReview(conn);
            System.out.println("Nombre d'utilisateurs sans reviews: " + usersWithoutReviewsCount + "\n");

            // Test 9: Les 10 utilisateurs avec le plus de reviews
            System.out.println("========================================");
            System.out.println("9. TOP 10 UTILISATEURS AVEC LE PLUS DE REVIEWS");
            System.out.println("========================================");
            List<User> topUsers = UserDAO.getTopUsersByReviewCount(conn, 10);
            for (User user : topUsers) {
                System.out.println(user);
            }
            System.out.println("Nombre d'utilisateurs récupérés: " + topUsers.size());

            System.out.println("\n===== FIN DES TESTS =====");

            // Fermer la connexion
            connexion.closeConnection();
        } else {
            System.err.println("Impossible de se connecter à la base de données!");
        }
    }
}
