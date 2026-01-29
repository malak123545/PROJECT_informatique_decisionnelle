public class ExploreTablesStructure {
    public static void main(String[] args) {
        connexion.getConnection();

        System.out.println("=== Structure de la table ELITE ===");
        connexion.afficherColonnesTable("elite");
        connexion.afficherPremiereLigneTable("yelp.elite");

        System.out.println("\n\n=== Structure de la table FRIEND ===");
        connexion.afficherColonnesTable("friend");
        connexion.afficherPremiereLigneTable("yelp.friend");

        connexion.closeConnection();
    }
}
