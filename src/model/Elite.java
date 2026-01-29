package model;

public class Elite {
    private String userId;
    private int year;

    // Constructeur par défaut
    public Elite() {
    }

    // Constructeur avec tous les paramètres
    public Elite(String userId, int year) {
        this.userId = userId;
        this.year = year;
    }

    // Getters et Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    @Override
    public String toString() {
        return "Elite{" +
                "userId='" + userId + '\'' +
                ", year=" + year +
                '}';
    }
}
