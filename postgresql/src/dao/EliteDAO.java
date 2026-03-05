package dao;

import model.Elite;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EliteDAO {

    /**
     * Récupérer tous les enregistrements elite de la base de données
     */
    public static List<Elite> getAllElites(Connection conn) {
        List<Elite> elites = new ArrayList<>();
        String query = "SELECT * FROM yelp.elite";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Elite elite = new Elite();
                elite.setUserId(rs.getString("user_id"));
                elite.setYear(rs.getInt("year"));
                elites.add(elite);
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des elites!");
            e.printStackTrace();
        }

        return elites;
    }

    /**
     * Récupérer les années elite d'un utilisateur spécifique
     */
    public static List<Elite> getElitesByUserId(Connection conn, String userId) {
        List<Elite> elites = new ArrayList<>();
        String query = "SELECT * FROM yelp.elite WHERE user_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Elite elite = new Elite();
                elite.setUserId(rs.getString("user_id"));
                elite.setYear(rs.getInt("year"));
                elites.add(elite);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des elites!");
            e.printStackTrace();
        }

        return elites;
    }

    /**
     * Récupérer les N premiers enregistrements elite
     */
    public static List<Elite> getElitesLimit(Connection conn, int limit) {
        List<Elite> elites = new ArrayList<>();
        String query = "SELECT * FROM yelp.elite LIMIT ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Elite elite = new Elite();
                elite.setUserId(rs.getString("user_id"));
                elite.setYear(rs.getInt("year"));
                elites.add(elite);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des elites!");
            e.printStackTrace();
        }

        return elites;
    }

    /**
     * Récupérer tous les utilisateurs elite pour une année donnée
     */
    public static List<Elite> getElitesByYear(Connection conn, int year) {
        List<Elite> elites = new ArrayList<>();
        String query = "SELECT * FROM yelp.elite WHERE year = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, year);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Elite elite = new Elite();
                elite.setUserId(rs.getString("user_id"));
                elite.setYear(rs.getInt("year"));
                elites.add(elite);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des elites!");
            e.printStackTrace();
        }

        return elites;
    }
}
