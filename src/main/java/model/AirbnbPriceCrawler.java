package model;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirbnbPriceCrawler {
    private EdgeDriver driver;
    private WebDriverWait wait;


    public AirbnbPriceCrawler() {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public void crawl() throws InterruptedException, IOException {
        String searchUrl = "https://www.airbnb.cz/s/Praha/homes?currency=EUR";
        driver.get(searchUrl);
        Thread.sleep(5000);

        List<Listing> listings = new ArrayList<>();
        HashSet<Host> hosts = new HashSet<>();
        int pageNum = 2;

        while (true) {
            Thread.sleep(3000);

            List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid='card-container']"));
            System.out.println("Na strance " + (pageNum - 1) + " se naslo tolik nabidek: " + cards.size());

            for (WebElement card : cards) {
                try {
                    //nazev
                    String title = card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText();
                    Listing listing = new Listing(title);

                    // Odkaz na detail nabidky
                    String href = card.findElement(By.cssSelector("a[href*='/rooms/']")).getAttribute("href");
                    listing.setUrl(href);
                    crawlFromCard(driver, listing, href);
                    listings.add(listing);
                } catch (Exception e) {
                    System.out.println("Chyba pri zpracovani karty: " + e.getMessage());
                }
            }

            // Přechod na další stránku:
            List<WebElement> buttons = driver.findElements(By.xpath("//a[text()='" + pageNum + "']"));
            if (!buttons.isEmpty()) {
                try {
                    buttons.get(0).click();
                    pageNum++;
                } catch (Exception e) {
                    System.out.println("Chyba pri kliknuti na dalsi stranku");
                    break;
                }
            } else {
                System.out.println("Posledni stranka, ukonceni.");
                break;
            }


        }

        // Uložení JSON souboru na konci:
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("airbnb_results.json")) {
            gson.toJson(listings, writer);
            System.out.println("Ulozeno " + listings.size() + " nabidek do JSON.");
        }
    }

    public void crawlFromCard(WebDriver driver, Listing listing, String url) {
        String originalWindow = driver.getWindowHandle();
        ((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", url);

        try {
            // Pokus o zavření případného popup okna
            WebElement closePopupButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[aria-label='Zavřít']")));
            closePopupButton.click();
            Thread.sleep(1500);
        } catch (TimeoutException | NoSuchElementException | InterruptedException ignored) {
        }

        Set<String> allWindows = driver.getWindowHandles();
        for (String windowHandle : allWindows) {
            if (!windowHandle.equals(originalWindow)) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            LocalDate startDate = LocalDate.now().plusDays(1); // завтра
            LocalDate endDate = startDate.plusDays(365);

            while (!startDate.isAfter(endDate.minusDays(1))) {
                LocalDate checkIn = startDate;
                LocalDate checkOut = checkIn.plusDays(1);

                try {
                    selectDates(driver, checkIn, checkOut);
                    double totalPrice = extractTotalPrice(driver);

                    listing.addPrice(new Listing.Price(
                            java.sql.Date.valueOf(checkIn),
                            (int) Math.round(totalPrice)
                    ));

                    System.out.println("✔ Cena " + checkIn + " - " + totalPrice + "€");

                } catch (Exception e) {
                    System.out.println("✖ Datum přeskočeno: " + checkIn);
                }

                startDate = startDate.plusDays(1);
            }

        } finally {
            driver.close();
            driver.switchTo().window(originalWindow);
        }
    }


    private void selectDates(WebDriver driver, LocalDate checkIn, LocalDate checkOut) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement calendarButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("button[aria-label*='Změnit termín']"))
        );
        calendarButton.click();

        clickDateInCalendar(driver, checkIn);
        clickDateInCalendar(driver, checkOut);

        // Подождать, пока календарь исчезнет
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("div[class*='calendar']")));
    }

    private void clickDateInCalendar(WebDriver driver, LocalDate date) {
        String dayString = String.valueOf(date.getDayOfMonth());
        String monthYear = date.getMonth().getDisplayName(TextStyle.FULL, new Locale("cs", "CZ")) + " " + date.getYear();

        for (int i = 0; i < 12; i++) {
            try {
                WebElement calendarMonth = driver.findElement(By.xpath("//strong[contains(text(),'" + monthYear + "')]"));
                List<WebElement> days = calendarMonth.findElement(By.xpath("..")).findElements(By.tagName("td"));

                for (WebElement cell : days) {
                    if (cell.getText().equals(dayString)) {
                        cell.click();
                        return;
                    }
                }
            } catch (Exception e) {
                WebElement nextButton = driver.findElement(By.cssSelector("button[aria-label='Další']"));
                nextButton.click();
                new WebDriverWait(driver, Duration.ofSeconds(2)).until(ExpectedConditions.stalenessOf(nextButton));
            }
        }

        throw new NoSuchElementException("Datum nenalezeno v kalendáři: " + date);
    }

    private double extractTotalPrice(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement priceEl = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("div._182z7aq1 span._j1kt73")
        ));
        String priceText = priceEl.getText(); // например, "€ 107,09"
        return parseDoublePrice(priceText);
    }

    private double parseDoublePrice(String text) {
        String clean = text.replaceAll("[^0-9,]", "").replace(",", ".");
        return Double.parseDouble(clean);
    }





    public void closePopupIfPresent() {
        try {
            WebElement closeButton = driver.findElement(By.cssSelector("button[aria-label='Zavřít']"));
            if (closeButton.isDisplayed()) {
                closeButton.click();
                Thread.sleep(1000); // почекати закриття поп-апа
                System.out.println("Pop-up byl zavren.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("Pop-up nebyl nalezen.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}