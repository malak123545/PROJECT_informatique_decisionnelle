import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

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
     * Afficher les noms de toutes les tables dans la base de données
     */
    public static void afficherTablesDatabase() {
        Connection conn = getConnection();
        if (conn == null) {
            System.err.println("Impossible d'obtenir la connexion!");
            return;
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});

            System.out.println("\n========================================");
            System.out.println("TABLES DANS LA BASE DE DONNÉES");
            System.out.println("========================================");

            int count = 0;
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableSchema = tables.getString("TABLE_SCHEM");
                System.out.println((++count) + ". " + tableSchema + "." + tableName);
            }

            if (count == 0) {
                System.out.println("Aucune table trouvée.");
            } else {
                System.out.println("\nTotal: " + count + " table(s)");
            }

            tables.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des tables!");
            e.printStackTrace();
        }
    }

    /**
     * Afficher les colonnes d'une table spécifique
     */
    public static void afficherColonnesTable(String nomTable) {
        Connection conn = getConnection();
        if (conn == null) {
            System.err.println("Impossible d'obtenir la connexion!");
            return;
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, nomTable, null);

            System.out.println("\n========================================");
            System.out.println("COLONNES DE LA TABLE: " + nomTable);
            System.out.println("========================================");

            int count = 0;
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                String isNullable = columns.getString("IS_NULLABLE");

                System.out.printf("%d. %-30s | Type: %-15s | Taille: %-5d | Nullable: %s%n",
                        ++count, columnName, columnType, columnSize, isNullable);
            }

            if (count == 0) {
                System.out.println("Aucune colonne trouvée pour la table: " + nomTable);
            } else {
                System.out.println("\nTotal: " + count + " colonne(s)");
            }

            columns.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des colonnes de la table: " + nomTable);
            e.printStackTrace();
        }
    }

    /**
     * Afficher la première ligne d'une table
     * @param nomTable Le nom de la table (peut inclure le schéma, ex: "madm.customer" ou juste "customer")
     */
    public static void afficherPremiereLigneTable(String nomTable) {
        Connection conn = getConnection();
        if (conn == null) {
            System.err.println("Impossible d'obtenir la connexion!");
            return;
        }

        Statement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.createStatement();
            // Si le nom de table ne contient pas de point, chercher dans tous les schémas
            String query;
            if (!nomTable.contains(".")) {
                query = "SELECT * FROM \"" + nomTable + "\" LIMIT 1";
            } else {
                // Utiliser le nom complet avec schéma
                query = "SELECT * FROM " + nomTable + " LIMIT 1";
            }

            rs = stmt.executeQuery(query);

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            System.out.println("\n========================================");
            System.out.println("PREMIÈRE LIGNE DE LA TABLE: " + nomTable);
            System.out.println("========================================");

            if (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    String columnValue = rs.getString(i);
                    System.out.printf("%-30s : %s%n", columnName, columnValue);
                }
            } else {
                System.out.println("La table est vide (aucune ligne trouvée).");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération de la première ligne de la table: " + nomTable);
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Méthode main pour tester la connexion et les fonctions d'affichage
     */
    public static void main(String[] args) {
        // Test de connexion
        Connection conn = connexion.getConnection();

        if (conn != null) {
            System.out.println("Test de connexion réussi!");

            // Afficher toutes les tables
            afficherTablesDatabase();

            // Si vous voulez tester les colonnes et première ligne d'une table spécifique
            // Décommentez et modifiez avec le nom d'une de vos tables:
             afficherColonnesTable("yelp.review");
            afficherPremiereLigneTable("yelp.review");

                afficherColonnesTable("yelp.elite");
            afficherPremiereLigneTable("yelp.elite");

                afficherColonnesTable("yelp.friend");
            afficherPremiereLigneTable("yelp.friend");

                afficherColonnesTable("yelp.user");
            afficherPremiereLigneTable("yelp.user");

            // Fermer la connexion
            connexion.closeConnection();
        } else {
            System.out.println("Échec de la connexion!");
        }
    }
}
