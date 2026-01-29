import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class connexion {

    // Paramètres de connexion
    private static final String URL = "jdbc:postgresql://stendhal.iem:5432/tpid2020";
    private static final String USER = "tpid";
    private static final String PASSWORD = "tpid";

    // Instance de connexion
    private static Connection connection = null;

    /**
     * Obtenir une connexion à la base de données
     */
    public static Connection getConnection() {
        if (connection == null) {
            try {
                // Charger le driver PostgreSQL
                Class.forName("org.postgresql.Driver");

                // Établir la connexion
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Connexion à la base de données réussie!");

            } catch (ClassNotFoundException e) {
                System.err.println("Driver PostgreSQL non trouvé!");
                e.printStackTrace();
            } catch (SQLException e) {
                System.err.println("Erreur lors de la connexion à la base de données!");
                e.printStackTrace();
            }
        }
        return connection;
    }

    /**
     * Fermer la connexion à la base de données
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("Connexion fermée.");
            } catch (SQLException e) {
                System.err.println("Erreur lors de la fermeture de la connexion!");
                e.printStackTrace();
            }
        }
    }

    /**
     * Méthode main pour tester la connexion
     */
    public static void main(String[] args) {
        // Test de connexion
        Connection conn = connexion.getConnection();

        if (conn != null) {
            System.out.println("Test de connexion réussi!");

            // Fermer la connexion
            connexion.closeConnection();
        } else {
            System.out.println("Échec de la connexion!");
        }
    }
}
