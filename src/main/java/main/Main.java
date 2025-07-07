package main;

import java.io.FileWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
        String link;

        public Listing(String title, String link) {
            this.title = title;
            //this.price = price;
            this.link = link;
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Napiste mesto v anglictine (napr. Prague, Brno): ");
        String city = scanner.nextLine().trim();

        // HTML forming
        String searchUrl = "https://www.booking.com/searchresults.html?ss=" +
                URLEncoder.encode(city, StandardCharsets.UTF_8);

        // Driver
        WebDriver driver = new EdgeDriver();
        driver.get(searchUrl);

        //Page loading
        Thread.sleep(5000);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
            //Automatic scrolling and buttonpressing
        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(5000); // подожди, пока подгрузится

            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == lastHeight) {
                // Пробуем найти кнопку "Load more results"
                List<WebElement> buttons = driver.findElements(By.xpath("//button[span[text()='Load more results']]"));
                if (!buttons.isEmpty()) {
                    buttons.get(0).click();
                    Thread.sleep(2000);
                } else {
                    System.out.println("Prosli jsme vsemi nabidkami. Zacina se ulozeni do souboru");
                    break; // кнопки нет — выходим из цикла
                }
            }

            lastHeight = newHeight;
        }

        // Accepting cards with offers
        List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid=\"property-card\"]"));
        List<Listing> results = new ArrayList<>();

        for (WebElement card : cards) {
            try {
                String title = card.findElement(By.cssSelector("[data-testid=\"title\"]")).getText();
                //String price = card.findElement(By.cssSelector("[data-testid=\"price-and-discounted-price\"]")).getText();
String url = card.findElement(By.cssSelector("[data-testid=\"title-link\"]")).getAttribute("href");

                results.add(new Listing(title, url));
            } catch (Exception e) {
                continue;
            }
        }

        driver.quit();

        // Saving with GSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("booking_results.json")) {
            gson.toJson(results, writer);
        }

        System.out.println("Hotovo! Mame " + results.size() + " nabidek v booking_results.json");
    }


}
