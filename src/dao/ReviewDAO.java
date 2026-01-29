package dao;

import model.Review;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReviewDAO {

    /**
     * Récupérer toutes les reviews de la base de données
     */
    public static List<Review> getAllReviews(Connection conn) {
        List<Review> reviews = new ArrayList<>();
        String query = "SELECT * FROM yelp.review";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Review review = extractReviewFromResultSet(rs);
                reviews.add(review);
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des reviews!");
            e.printStackTrace();
        }

        return reviews;
    }

    /**
     * Récupérer une review par son ID
     */
    public static Review getReviewById(Connection conn, String reviewId) {
        String query = "SELECT * FROM yelp.review WHERE review_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, reviewId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Review review = extractReviewFromResultSet(rs);
                rs.close();
                return review;
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération de la review!");
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Récupérer les reviews d'un utilisateur spécifique
     */
    public static List<Review> getReviewsByUserId(Connection conn, String userId) {
        List<Review> reviews = new ArrayList<>();
        String query = "SELECT * FROM yelp.review WHERE user_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Review review = extractReviewFromResultSet(rs);
                reviews.add(review);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des reviews!");
            e.printStackTrace();
        }

        return reviews;
    }

    /**
     * Récupérer les N premières reviews
     */
    public static List<Review> getReviewsLimit(Connection conn, int limit) {
        List<Review> reviews = new ArrayList<>();
        String query = "SELECT * FROM yelp.review LIMIT ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Review review = extractReviewFromResultSet(rs);
                reviews.add(review);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des reviews!");
            e.printStackTrace();
        }

        return reviews;
    }

    /**
     * Méthode helper pour extraire une Review d'un ResultSet
     */
    private static Review extractReviewFromResultSet(ResultSet rs) throws SQLException {
        Review review = new Review();
        review.setBusinessId(rs.getString("business_id"));
        review.setCool(rs.getLong("cool"));
        review.setDate(rs.getDate("date"));
        review.setFunny(rs.getLong("funny"));
        review.setReviewId(rs.getString("review_id"));
        review.setStars(rs.getDouble("stars"));
        review.setText(rs.getString("text"));
        review.setUseful(rs.getLong("useful"));
        review.setUserId(rs.getString("user_id"));
        review.setSparkPartition(rs.getInt("spark_partition"));
        return review;
    }
}
