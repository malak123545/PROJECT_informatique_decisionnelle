package dao;

import model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    /**
     * Récupérer tous les utilisateurs de la base de données
     */
    public static List<User> getAllUsers(Connection conn) {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM yelp.user";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                User user = new User();
                user.setAverageStars(rs.getDouble("average_stars"));
                user.setComplimentCool(rs.getLong("compliment_cool"));
                user.setComplimentCute(rs.getLong("compliment_cute"));
                user.setComplimentFunny(rs.getLong("compliment_funny"));
                user.setComplimentHot(rs.getLong("compliment_hot"));
                user.setComplimentList(rs.getLong("compliment_list"));
                user.setComplimentMore(rs.getLong("compliment_more"));
                user.setComplimentNote(rs.getLong("compliment_note"));
                user.setComplimentPhotos(rs.getLong("compliment_photos"));
                user.setComplimentPlain(rs.getLong("compliment_plain"));
                user.setComplimentProfile(rs.getLong("compliment_profile"));
                user.setComplimentWriter(rs.getLong("compliment_writer"));
                user.setCool(rs.getLong("cool"));
                user.setFans(rs.getLong("fans"));
                user.setFunny(rs.getLong("funny"));
                user.setName(rs.getString("name"));
                user.setReviewCount(rs.getLong("review_count"));
                user.setUseful(rs.getLong("useful"));
                user.setUserId(rs.getString("user_id"));
                user.setYelpingSince(rs.getDate("yelping_since"));

                users.add(user);
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des utilisateurs!");
            e.printStackTrace();
        }

        return users;
    }

    /**
     * Récupérer un utilisateur par son ID
     */
    public static User getUserById(Connection conn, String userId) {
        String query = "SELECT * FROM yelp.user WHERE user_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                User user = new User();
                user.setAverageStars(rs.getDouble("average_stars"));
                user.setComplimentCool(rs.getLong("compliment_cool"));
                user.setComplimentCute(rs.getLong("compliment_cute"));
                user.setComplimentFunny(rs.getLong("compliment_funny"));
                user.setComplimentHot(rs.getLong("compliment_hot"));
                user.setComplimentList(rs.getLong("compliment_list"));
                user.setComplimentMore(rs.getLong("compliment_more"));
                user.setComplimentNote(rs.getLong("compliment_note"));
                user.setComplimentPhotos(rs.getLong("compliment_photos"));
                user.setComplimentPlain(rs.getLong("compliment_plain"));
                user.setComplimentProfile(rs.getLong("compliment_profile"));
                user.setComplimentWriter(rs.getLong("compliment_writer"));
                user.setCool(rs.getLong("cool"));
                user.setFans(rs.getLong("fans"));
                user.setFunny(rs.getLong("funny"));
                user.setName(rs.getString("name"));
                user.setReviewCount(rs.getLong("review_count"));
                user.setUseful(rs.getLong("useful"));
                user.setUserId(rs.getString("user_id"));
                user.setYelpingSince(rs.getDate("yelping_since"));

                rs.close();
                return user;
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération de l'utilisateur!");
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Récupérer les N premiers utilisateurs
     */
    public static List<User> getUsersLimit(Connection conn, int limit) {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM yelp.user LIMIT ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                User user = new User();
                user.setAverageStars(rs.getDouble("average_stars"));
                user.setComplimentCool(rs.getLong("compliment_cool"));
                user.setComplimentCute(rs.getLong("compliment_cute"));
                user.setComplimentFunny(rs.getLong("compliment_funny"));
                user.setComplimentHot(rs.getLong("compliment_hot"));
                user.setComplimentList(rs.getLong("compliment_list"));
                user.setComplimentMore(rs.getLong("compliment_more"));
                user.setComplimentNote(rs.getLong("compliment_note"));
                user.setComplimentPhotos(rs.getLong("compliment_photos"));
                user.setComplimentPlain(rs.getLong("compliment_plain"));
                user.setComplimentProfile(rs.getLong("compliment_profile"));
                user.setComplimentWriter(rs.getLong("compliment_writer"));
                user.setCool(rs.getLong("cool"));
                user.setFans(rs.getLong("fans"));
                user.setFunny(rs.getLong("funny"));
                user.setName(rs.getString("name"));
                user.setReviewCount(rs.getLong("review_count"));
                user.setUseful(rs.getLong("useful"));
                user.setUserId(rs.getString("user_id"));
                user.setYelpingSince(rs.getDate("yelping_since"));

                users.add(user);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des utilisateurs!");
            e.printStackTrace();
        }

        return users;
    }
}
