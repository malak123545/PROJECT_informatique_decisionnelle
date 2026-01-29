package dao;

import model.Friend;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendDAO {

    /**
     * Récupérer toutes les relations d'amitié de la base de données
     */
    public static List<Friend> getAllFriends(Connection conn) {
        List<Friend> friends = new ArrayList<>();
        String query = "SELECT * FROM yelp.friend";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Friend friend = new Friend();
                friend.setUserId(rs.getString("user_id"));
                friend.setFriendId(rs.getString("friend_id"));
                friends.add(friend);
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des amis!");
            e.printStackTrace();
        }

        return friends;
    }

    /**
     * Récupérer tous les amis d'un utilisateur spécifique
     */
    public static List<Friend> getFriendsByUserId(Connection conn, String userId) {
        List<Friend> friends = new ArrayList<>();
        String query = "SELECT * FROM yelp.friend WHERE user_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Friend friend = new Friend();
                friend.setUserId(rs.getString("user_id"));
                friend.setFriendId(rs.getString("friend_id"));
                friends.add(friend);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des amis!");
            e.printStackTrace();
        }

        return friends;
    }

    /**
     * Récupérer les N premières relations d'amitié
     */
    public static List<Friend> getFriendsLimit(Connection conn, int limit) {
        List<Friend> friends = new ArrayList<>();
        String query = "SELECT * FROM yelp.friend LIMIT ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Friend friend = new Friend();
                friend.setUserId(rs.getString("user_id"));
                friend.setFriendId(rs.getString("friend_id"));
                friends.add(friend);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des amis!");
            e.printStackTrace();
        }

        return friends;
    }

    /**
     * Compter le nombre d'amis d'un utilisateur
     */
    public static int countFriendsByUserId(Connection conn, String userId) {
        String query = "SELECT COUNT(*) as count FROM yelp.friend WHERE user_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int count = rs.getInt("count");
                rs.close();
                return count;
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors du comptage des amis!");
            e.printStackTrace();
        }

        return 0;
    }
}
