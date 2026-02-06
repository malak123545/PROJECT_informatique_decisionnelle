package model;

import java.sql.Date;

public class Review {
    private String businessId;
    private long cool;
    private Date date;
    private long funny;
    private String reviewId;
    private double stars;
    private String text;
    private long useful;
    private String userId;
    private int sparkPartition;

    // Constructeur par défaut
    public Review() {
    }

    // Constructeur avec tous les paramètres
    public Review(String businessId, long cool, Date date, long funny, String reviewId,
                  double stars, String text, long useful, String userId, int sparkPartition) {
        this.businessId = businessId;
        this.cool = cool;
        this.date = date;
        this.funny = funny;
        this.reviewId = reviewId;
        this.stars = stars;
        this.text = text;
        this.useful = useful;
        this.userId = userId;
        this.sparkPartition = sparkPartition;
    }

    // Getters et Setters
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }

    public long getCool() { return cool; }
    public void setCool(long cool) { this.cool = cool; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public long getFunny() { return funny; }
    public void setFunny(long funny) { this.funny = funny; }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public double getStars() { return stars; }
    public void setStars(double stars) { this.stars = stars; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getUseful() { return useful; }
    public void setUseful(long useful) { this.useful = useful; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getSparkPartition() { return sparkPartition; }
    public void setSparkPartition(int sparkPartition) { this.sparkPartition = sparkPartition; }

    @Override
    public String toString() {
        return "Review{" +
                "reviewId='" + reviewId + '\'' +
                ", userId='" + userId + '\'' +
                ", businessId='" + businessId + '\'' +
                ", stars=" + stars +
                ", date=" + date +
                ", text='" + (text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
                ", useful=" + useful +
                ", funny=" + funny +
                ", cool=" + cool +
                '}';
    }
}
