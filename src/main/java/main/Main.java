package main;

import java.io.FileWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.io.IOException;


public class Main {
    static class Listing {
        String title;
        //String price;
        //String link;

        public Listing(String title ) {
            this.title = title;
            //this.price = price;
            //this.link = link;
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Napiste mesto v anglictine (napr. Prague): ");
        String city = scanner.nextLine().trim();

        // HTML forming
        String searchUrl = "https://www.booking.com/searchresults.html?ss=" +
                URLEncoder.encode(city, StandardCharsets.UTF_8);

        // Driver
        WebDriver driver = new EdgeDriver();
        driver.get(searchUrl);

        // Даем странице загрузиться (можно настроить WebDriverWait)
        Thread.sleep(5000);

        // Получаем карточки предложений
        List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid=\"property-card\"]"));
        List<Listing> results = new ArrayList<>();

        for (WebElement card : cards) {
            try {
                String title = card.findElement(By.cssSelector("[data-testid=\"title\"]")).getText();
                //String price = card.findElement(By.cssSelector("[data-testid=\"price-and-discounted-price\"]")).getText();
//String url = card.findElement(By.cssSelector("[data-testid=\"title-link\"]")).getAttribute("href");

                results.add(new Listing(title));
            } catch (Exception e) {
                // Если у карточки чего-то не хватает — пропускаем
                continue;
            }
        }

        driver.quit();

        // Сохраняем в JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("booking_results.json")) {
            gson.toJson(results, writer);
        }

        System.out.println("Hotovo! Mame " + results.size() + " nabidek v booking_results.json");
    }


}
