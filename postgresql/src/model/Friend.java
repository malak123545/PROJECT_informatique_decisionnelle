package model;

public class Friend {
    private String userId;
    private String friendId;

    // Constructeur par défaut
    public Friend() {
    }

    // Constructeur avec tous les paramètres
    public Friend(String userId, String friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }

    // Getters et Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFriendId() { return friendId; }
    public void setFriendId(String friendId) { this.friendId = friendId; }

    @Override
    public String toString() {
        return "Friend{" +
                "userId='" + userId + '\'' +
                ", friendId='" + friendId + '\'' +
                '}';
    }
}
