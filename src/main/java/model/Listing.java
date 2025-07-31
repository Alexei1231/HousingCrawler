package model;

import java.util.ArrayList;
import java.util.Date;

public class Listing {
    private String title;
    private String checkInDate;
    private double pricePerNightEur;
    private String url;
    private int maxGuests;
    private int bedrooms;
    private int beds;
    private int bathrooms;
    private double rating;
    private int reviewCount;
    private String description;
    private String location;

    private Host host;

    ArrayList<Price> priceArrayList;

    public static class Price{
        Date date;
        double price;

        public Price(Date date, double price) {
            this.date = date;
            this.price = price;
        }
    }

    public void addPrice(Price price) {
        this.priceArrayList.add(price);
    }

    public ArrayList<Price> getPriceArrayList() {
        return priceArrayList;
    }

    public double getPricePerNightEur() {
        return pricePerNightEur;
    }

    public void setPricePerNightEur(double pricePerNightEur) {
        this.pricePerNightEur = pricePerNightEur;
    }


    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }


    // Конструктор, геттеры и сеттеры
    public Listing(String title) {
        this.title = title;
    }

    // Геттеры и сеттеры
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getMaxGuests() {
        return maxGuests;
    }

    public void setMaxGuests(int maxGuests) {
        this.maxGuests = maxGuests;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(int bedrooms) {
        this.bedrooms = bedrooms;
    }

    public int getBeds() {
        return beds;
    }

    public void setBeds(int beds) {
        this.beds = beds;
    }

    public int getBathrooms() {
        return bathrooms;
    }

    public void setBathrooms(int bathrooms) {
        this.bathrooms = bathrooms;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

//    //public double getPricePerNightCzk() {
//        return pricePerNightCzk;
//    }
//
//    public void setPricePerNightCzk(double pricePerNightCzk) {
//        this.pricePerNightCzk = pricePerNightCzk;
//    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(String checkInDate) {
        this.checkInDate = checkInDate;
    }
}