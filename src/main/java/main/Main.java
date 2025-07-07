package main;

import java.io.FileWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

public class Main {
    static class Listing {
        String title;
        String price;
        String link;

        public Listing(String title, String price, String link) {
            this.title = title;
            this.price = price;
            this.link = link;
        }
    }


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Napiste nazev mesta v anglictine(tr. Prague): ");
        String city = scanner.nextLine().trim();

        String seedUrl = "https://www.booking.com/searchresults.html";//target webpage

        seedUrl += "?ss=" + URLEncoder.encode(city, StandardCharsets.UTF_8); //adding the city to the
        //targeted webpage

        Document doc = retrieveHTML(seedUrl);

        if (doc != null) {
            System.out.println("HTML successfully retrieved!");
        }
        Elements listings = doc.select("div[data-testid=property-card]");
        ArrayList<Listing> results = new ArrayList<>();

        for (Element listing : listings) {
            String title = listing.selectFirst("div[data-testid=title]") != null
                    ? listing.selectFirst("div[data-testid=title]").text()
                    : "Není název";

            String price = listing.selectFirst("span[data-testid=price-and-discounted-price]") != null
                    ? listing.selectFirst("span[data-testid=price-and-discounted-price]").text()
                    : "Není cena";

            String link = listing.selectFirst("a[data-testid=title-link]") != null
                    ? "https://www.booking.com" + listing.selectFirst("a[data-testid=title-link]").attr("href").split("\\?")[0]
                    : "Není link";

            results.add(new Listing(title, price, link));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter fileWriter = new FileWriter("results.json")) {
            gson.toJson(results, fileWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Готово! Найдено " + results.size() + " предложений.");
        System.out.println("Результаты сохранены в results.json");
    }

    private static Document retrieveHTML(String url) { //method which retrieves the whole HTML page
        try {
            // download HTML document using JSoup's connect class
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36")
                    .timeout(10000).get();//complicated(rather then just .get) in
            // order to bypass Booking anti-bot protection

//            // log the HTML content
//            System.out.println(doc.html());

            // return the HTML document
            return doc;
        } catch (IOException e) {
            // handle exceptions
            System.err.println("Unable to fetch HTML of: " + url);
        }
        return null;
    }
}
