package model;

import org.jspecify.annotations.Nullable;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.swing.*;
import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirbnbHostCrawler {
    private EdgeDriver driver;
    private WebDriverWait wait;
    private String url;//url to the host website

    public AirbnbHostCrawler(String url) {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        url.trim();
        this.url = url;
    }

    public void crawl() throws InterruptedException { // tady se bude nachazet logika prohledavani stranky hostitele
        // a zbirani dat o hostitelovi
        Thread.sleep(5000);
        driver.get(url);
        Thread.sleep(5000);
        Host host = new Host();
        //!!!!!NAME AND SUPERHOST!!!!!
        try {
            // Najdeme element se jménem hostitele - v tomto případě span uvnitř h1
            WebElement nameSpan = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1 span")));
            String hostName = nameSpan.getText().trim();
            host.setName(hostName);
            System.out.println("Jméno hostitele: " + hostName);
        } catch (Exception e) {
            System.out.println("Jméno hostitele nebylo nalezeno");
        }

        try {
            // Najdeme element, který obsahuje text 'Superhostitel'
            // Například najdeme span obsahující text 'Superhostitel'
            WebElement superhostSpan = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[contains(text(),'Superhostitel')]")));
            host.setSuperhost(true);
            System.out.println("Hostitel je Superhostitel");
        } catch (Exception e) {
            // Pokud nenalezeno, není superhost
            host.setSuperhost(false);
            System.out.println("Hostitel není Superhostitel");
        }
        //!!!!!AVERAGE RATING AND RATING COUNT!!!!!
        try {
            // Najdeme kontejner s celkovými informacemi o recenzích (hledáme podle textu "recenzí" ve span elementu)
            WebElement reviewsContainer = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[contains(text(), 'recenzí')]/ancestor::div[2]")));

            // V tomto kontejneru najdeme všechny elementy s atributem data-testid="Hodnocení-stat-heading"
            List<WebElement> ratingElements = reviewsContainer.findElements(By.cssSelector("span[data-testid='Hodnocení-stat-heading']"));

            if (ratingElements.size() >= 2) {
                // První element představuje počet recenzí
                String reviewCountText = ratingElements.get(0).findElement(By.tagName("div")).getText().trim();
                // Druhý element představuje průměrné hodnocení
                String averageRatingText = ratingElements.get(1).findElement(By.tagName("div")).getText().trim();

                // Parsujeme počet recenzí (podobně jako v původním kódu)
                String cleanedCount = reviewCountText.replace("\u00A0", "").replace(" ", "").replace(",", ".");
                double reviewCountDouble = Double.parseDouble(cleanedCount);
                int reviewCount = (int) reviewCountDouble;
                host.setReviewCount(reviewCount);

                // Parsujeme průměrné hodnocení (s čárkou, převedeme na double)
                String cleanedAverage = averageRatingText.replace("\u00A0", "").replace(" ", "").replace(",", ".");
                double averageRating = Double.parseDouble(cleanedAverage);
                host.setAverageRating(averageRating);

                System.out.println("Počet recenzí: " + reviewCount);
                System.out.println("Průměrné hodnocení: " + averageRating);

            } else {
                System.out.println("Nebyl nalezen počet recenzí i průměrné hodnocení");
            }

        } catch (NoSuchElementException e) {
            System.out.println("Elementy hodnocení nebyly nalezeny");
        }
        //!!!!!TIME HOSTING!!!!!
        try {
            // Nejprve zkusíme najít počet let hostování
            WebElement yearsElement = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("span[data-testid='Let hostí-stat-heading']")));

            String yearsText = yearsElement.getText().trim();
            host.setHostingSince(yearsText + " let");
            System.out.println("Hostí již " + yearsText);

        } catch (Exception e1) {
            // Pokud roky nenajdeme, zkusíme měsíce
            try {
                WebElement monthsElement = new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("span[data-testid='Měsíců hostí-stat-heading']")));

                String monthsText = monthsElement.getText().trim();
                host.setHostingSince(monthsText + " měsíců");
                System.out.println("Hostí již " + monthsText);

            } catch (Exception e2) {
                System.out.println("Délka hostování nebyla nalezena");
            }
        }

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, 500)");

        //a tady budeme (podobne jako v metode crawl v tride AirbnbCrawler) pak pracovat i s jednotlivymi nabidkami metodou
        //initializujeme ArrayList pro hosta, ktery mu pak na konci "dame"
        ArrayList<Listing> listings = new ArrayList<>();


        WebElement showAllButton = null;
        ExecutorService executor = Executors.newFixedThreadPool(1);

        try { //v tomto try catch bloku proverime, zda bude najdene tlacitko, ktere otevira vsechny nabidky; pokud ne, tak pracujeme
            // ryze s tim, co je na strance uzivatele
            showAllButton = new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(".//button[starts-with(normalize-space(), 'Zobrazit všechny uživatelovy nabídky')]")));
            showAllButton.click();
            Thread.sleep(3000);

            List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid='card-container']"));
            System.out.println("Na strance se naslo tolik nabidek: " + cards.size());

            List<Callable<Void>> tasks = new ArrayList<>();

            for (WebElement card : cards) {
                try {
                    String title = card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText();
                    String href = card.findElement(By.cssSelector("a[href*='/rooms/']")).getAttribute("href");

                    Listing listing = new Listing(title);
                    listings.add(listing);

                    Callable<Void> task = () -> {
                        crawlFromCard(listing, href + "?locale=cs&currency=EUR");
                        return null;
                    };

                    tasks.add(task);

                } catch (Exception e) {
                    System.out.println("Chyba pri zpracovani karty: " + e.getMessage());
                }
            }
            // spoustime vsechny vlakna a cekame, az se dokonci prace s nimi
            try {
                List<Future<Void>> futures = executor.invokeAll(tasks);

                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (ExecutionException ee) {
                        System.out.println("Chyba v tasku: " + ee.getCause());
                    }
                }
            } catch (InterruptedException e2) {
                System.out.println("Přerušeno při čekání na dokončení úloh");
                executor.shutdownNow();
                throw e2;
            }
        } catch (TimeoutException e) {
            List<Callable<Void>> tasks = new ArrayList<>();
            List<WebElement> cards = driver.findElements(By.xpath("//div[.//div[@data-testid='listing-card-title']]"));
            System.out.println("Na strance se naslo tolik nabidek: " + cards.size());


            for (WebElement card : cards) {
                try {
                    String title = card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText();
                    String href = card.findElement(By.cssSelector("a[href*='/rooms/']")).getAttribute("href");

                    Listing listing = new Listing(title);
                    listings.add(listing);

                    Callable<Void> task = () -> {
                        crawlFromCard(listing, href + "?locale=cs&currency=EUR");
                        return null;
                    };

                    tasks.add(task);

                } catch (Exception e1) {
                    System.out.println("Chyba pri zpracovani karty: " + e1.getMessage());
                }
            }
            // spoustime vsechny vlakna a cekame, az se dokonci prace s nimi
            try {
                List<Future<Void>> futures = executor.invokeAll(tasks);

                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (ExecutionException ee) {
                        System.out.println("Chyba v tasku: " + ee.getCause());
                    }
                }
            } catch (InterruptedException e2) {
                System.out.println("Přerušeno při čekání na dokončení úloh");
                executor.shutdownNow();
                throw e2;
            }
        }


        host.setListings(listings);

    }

    public void crawlFromCard(Listing listing, String url) {
        // Vytvoříme nový EdgeDriver pro paralelní zpracování
        EdgeOptions options = new EdgeOptions();
        //options.addArguments("--headless=new"); // odkomentuj, pokud chceš běžet bez GUI
        WebDriver localDriver = new EdgeDriver(options);
        WebDriverWait wait = new WebDriverWait(localDriver, Duration.ofSeconds(10));

        try {
            localDriver.get(url);

            // Počkej 3 sekundy, aby se stránka načetla (případně můžeš změnit nebo odstranit)
            Thread.sleep(3000);

            // TODO: tady můžeš později přidat parsování informací z detailu

            System.out.println("Stránka načtena pro listing: " + listing.getTitle());

        } catch (Exception e) {
            System.out.println("Chyba při otevírání listingu: " + e.getMessage());
        } finally {
            localDriver.quit();
            System.out.println("Okno zavřeno pro listing: " + listing.getTitle());
        }
    }

}
