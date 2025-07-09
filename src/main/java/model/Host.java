package model;
import java.util.List;



public class Host {
    private String name;
    private int numberOfListings;
    private String profileUrl;
    private String info;
    private boolean isProfessional;
    private boolean isSuperhost;
    private int reviewCount;
    private String hostingSince;
    private double averageRating;
    private String responseRate;
    private String responseTime;

    // Конструктор, геттеры и сеттеры
    public Host(String name, String profileUrl) {
        this.name = name;
        this.profileUrl = profileUrl;
    }

    // Геттеры и сеттеры
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumberOfListings() {
        return numberOfListings;
    }

    public void setNumberOfListings(int numberOfListings) {
        this.numberOfListings = numberOfListings;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public boolean isProfessional() {
        return isProfessional;
    }

    public void setProfessional(boolean professional) {
        isProfessional = professional;
    }

    public boolean isSuperhost() {
        return isSuperhost;
    }

    public void setSuperhost(boolean superhost) {
        isSuperhost = superhost;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getHostingSince() {
        return hostingSince;
    }

    public void setHostingSince(String hostingSince) {
        this.hostingSince = hostingSince;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public String getResponseRate() {
        return responseRate;
    }

    public void setResponseRate(String responseRate) {
        this.responseRate = responseRate;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(String responseTime) {
        this.responseTime = responseTime;
    }
}