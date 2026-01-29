package model;

import java.sql.Date;

public class User {
    private double averageStars;
    private long complimentCool;
    private long complimentCute;
    private long complimentFunny;
    private long complimentHot;
    private long complimentList;
    private long complimentMore;
    private long complimentNote;
    private long complimentPhotos;
    private long complimentPlain;
    private long complimentProfile;
    private long complimentWriter;
    private long cool;
    private long fans;
    private long funny;
    private String name;
    private long reviewCount;
    private long useful;
    private String userId;
    private Date yelpingSince;

    // Constructeur par défaut
    public User() {
    }

    // Constructeur avec tous les paramètres
    public User(double averageStars, long complimentCool, long complimentCute, long complimentFunny,
                long complimentHot, long complimentList, long complimentMore, long complimentNote,
                long complimentPhotos, long complimentPlain, long complimentProfile, long complimentWriter,
                long cool, long fans, long funny, String name, long reviewCount, long useful,
                String userId, Date yelpingSince) {
        this.averageStars = averageStars;
        this.complimentCool = complimentCool;
        this.complimentCute = complimentCute;
        this.complimentFunny = complimentFunny;
        this.complimentHot = complimentHot;
        this.complimentList = complimentList;
        this.complimentMore = complimentMore;
        this.complimentNote = complimentNote;
        this.complimentPhotos = complimentPhotos;
        this.complimentPlain = complimentPlain;
        this.complimentProfile = complimentProfile;
        this.complimentWriter = complimentWriter;
        this.cool = cool;
        this.fans = fans;
        this.funny = funny;
        this.name = name;
        this.reviewCount = reviewCount;
        this.useful = useful;
        this.userId = userId;
        this.yelpingSince = yelpingSince;
    }

    // Getters et Setters
    public double getAverageStars() { return averageStars; }
    public void setAverageStars(double averageStars) { this.averageStars = averageStars; }

    public long getComplimentCool() { return complimentCool; }
    public void setComplimentCool(long complimentCool) { this.complimentCool = complimentCool; }

    public long getComplimentCute() { return complimentCute; }
    public void setComplimentCute(long complimentCute) { this.complimentCute = complimentCute; }

    public long getComplimentFunny() { return complimentFunny; }
    public void setComplimentFunny(long complimentFunny) { this.complimentFunny = complimentFunny; }

    public long getComplimentHot() { return complimentHot; }
    public void setComplimentHot(long complimentHot) { this.complimentHot = complimentHot; }

    public long getComplimentList() { return complimentList; }
    public void setComplimentList(long complimentList) { this.complimentList = complimentList; }

    public long getComplimentMore() { return complimentMore; }
    public void setComplimentMore(long complimentMore) { this.complimentMore = complimentMore; }

    public long getComplimentNote() { return complimentNote; }
    public void setComplimentNote(long complimentNote) { this.complimentNote = complimentNote; }

    public long getComplimentPhotos() { return complimentPhotos; }
    public void setComplimentPhotos(long complimentPhotos) { this.complimentPhotos = complimentPhotos; }

    public long getComplimentPlain() { return complimentPlain; }
    public void setComplimentPlain(long complimentPlain) { this.complimentPlain = complimentPlain; }

    public long getComplimentProfile() { return complimentProfile; }
    public void setComplimentProfile(long complimentProfile) { this.complimentProfile = complimentProfile; }

    public long getComplimentWriter() { return complimentWriter; }
    public void setComplimentWriter(long complimentWriter) { this.complimentWriter = complimentWriter; }

    public long getCool() { return cool; }
    public void setCool(long cool) { this.cool = cool; }

    public long getFans() { return fans; }
    public void setFans(long fans) { this.fans = fans; }

    public long getFunny() { return funny; }
    public void setFunny(long funny) { this.funny = funny; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getReviewCount() { return reviewCount; }
    public void setReviewCount(long reviewCount) { this.reviewCount = reviewCount; }

    public long getUseful() { return useful; }
    public void setUseful(long useful) { this.useful = useful; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Date getYelpingSince() { return yelpingSince; }
    public void setYelpingSince(Date yelpingSince) { this.yelpingSince = yelpingSince; }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", averageStars=" + averageStars +
                ", reviewCount=" + reviewCount +
                ", fans=" + fans +
                ", yelpingSince=" + yelpingSince +
                ", cool=" + cool +
                ", funny=" + funny +
                ", useful=" + useful +
                '}';
    }
}
