public class TestAffichage {
    public static void main(String[] args) {
        // Connexion
        connexion.getConnection();

        // Afficher toutes les tables
        connexion.afficherTablesDatabase();

        // Exemple: afficher les détails de la table "user" du schéma "yelp"
        System.out.println("\n\n");
        connexion.afficherColonnesTable("user");

        System.out.println("\n\n");
        connexion.afficherPremiereLigneTable("yelp.user");

        // Vous pouvez tester avec d'autres tables
        // Par exemple pour la table "review":
        System.out.println("\n\n");
        connexion.afficherColonnesTable("review");

        System.out.println("\n\n");
        connexion.afficherPremiereLigneTable("yelp.review");

        // Fermer la connexion
        connexion.closeConnection();
    }
}
