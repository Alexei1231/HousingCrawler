package model;

import org.openqa.selenium.*;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AirbnbCrawler {
    private EdgeDriver driver;
    private WebDriverWait wait;

    public AirbnbCrawler() {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public void crawl() throws InterruptedException, IOException {
        String searchUrl = "https://www.airbnb.cz/s/Praha/homes";
        driver.get(searchUrl);
        Thread.sleep(5000);

        List<WebElement> allCards = new ArrayList<>();
        int pageNum = 2;

        while (true) {
            Thread.sleep(4000);
            List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid='card-container']"));
            allCards.addAll(cards);
            System.out.println("na strance " + (pageNum - 1) + " se naslo tolik nabidek: " + cards.size());

//            try {
//                WebElement nextPageLink = driver.findElement(By.xpath("//a[text()='" + pageNum + "']"));
//                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", nextPageLink);
//                Thread.sleep(1000);
//                nextPageLink.click();
//                pageNum++;
//            } catch (Exception e) {
//                System.out.println("Pristi stranka " + pageNum + " nebyla nalezena. Konec.");
//                break;
//            }

            List<WebElement> buttons = driver.findElements(By.xpath("//a[text()='" + pageNum + "']"));
            if (!buttons.isEmpty()) {
                try {
                    buttons.get(0).click();
                    pageNum++;
                } catch (Exception e) {
                    break;
                }
                Thread.sleep(2000);
            } else {
                System.out.println("Prosli jsme vsemi nabidkami. Zacina se ukladani do souboru");
                break;
            }
        }

        List<Listing> listings = new ArrayList<>();
        for (WebElement card : allCards) {
            try {
                Listing listing = new Listing(card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText());
                //listing.setRating(Double.parseDouble(card.findElement(By.cssSelector("[data-testid='review-score']")).getText().split(" ")[0]));
                //listing.setReviewCount(Integer.parseInt(card.findElement(By.cssSelector("[data-testid='review-count']")).getText().split(" ")[0]));
                //listing.setPricePerNight(Double.parseDouble(card.findElement(By.cssSelector("[data-testid='price-and-discounted-price']")).getText().replaceAll("[^0-9]", "")));

                //prechod na stranku nabidky pro dalsi analyzu
                //String listingUrl = card.findElement(By.cssSelector("a")).getAttribute("href");

                // Получаем текущий дескриптор окна
                //String originalWindow = driver.getWindowHandle();

                // Открываем новую вкладку
                //((JavascriptExecutor) driver).executeScript("window.open('" + listingUrl + "', '_blank');");

                // Ждём, пока не появится новая вкладка
                //wait.until(ExpectedConditions.numberOfWindowsToBe(2));

                // Получаем все дескрипторы окон
                //Set<String> windows = driver.getWindowHandles();

                // Переключаемся на новую вкладку
//                for (String window : windows) {
//                    if (!window.equals(originalWindow)) {
//                        driver.switchTo().window(window);
//                        break;
//                    }
//                }

                // Ждём загрузки страницы
                //wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='listing-bed-label']")));

                // Сбор дополнительных данных
//                listing.setMaxGuests(Integer.parseInt(driver.findElement(By.cssSelector("[data-testid='listing-bed-label']")).getText().split(" ")[0]));
//                listing.setBedrooms(Integer.parseInt(driver.findElement(By.cssSelector("[data-testid='listing-bedrooms-label']")).getText().split(" ")[0]));
//                listing.setBeds(Integer.parseInt(driver.findElement(By.cssSelector("[data-testid='listing-beds-label']")).getText().split(" ")[0]));
//                listing.setBathrooms(Integer.parseInt(driver.findElement(By.cssSelector("[data-testid='listing-bathrooms-label']")).getText().split(" ")[0]));
//                listing.setDescription(driver.findElement(By.cssSelector("[data-testid='listing-description']")).getText());
//                listing.setLocation(driver.findElement(By.cssSelector("[data-testid='listing-location']")).getText());

                // Сбор данных о хосте
//                WebElement hostLink = driver.findElement(By.cssSelector("a[href*='/users/show/']"));
//                String hostName = hostLink.getText();
//                String hostUrl = hostLink.getAttribute("href");
//
//                Host host = new Host(hostName, hostUrl);
//                host.setSuperhost(driver.findElements(By.xpath("//*[contains(text(), 'Superhostitel')]")).size() > 0);
//
//                // Переход на страницу хоста для сбора дополнительных данных
//                driver.get(hostUrl);
//                Thread.sleep(3000);
//
//                host.setInfo(driver.findElement(By.cssSelector("[data-testid='user-profile-about']")).getText());
//                host.setHostingSince(driver.findElement(By.cssSelector("[data-testid='user-profile-hosting-since']")).getText());
//                host.setResponseRate(driver.findElement(By.cssSelector("[data-testid='user-profile-response-rate']")).getText());
//                host.setResponseTime(driver.findElement(By.cssSelector("[data-testid='user-profile-response-time']")).getText());

                //listing.setHost(host);

                // Закрываем текущую вкладку
                //driver.close();

                // Переключаемся обратно на оригинальную вкладку
                //driver.switchTo().window(originalWindow);

                listings.add(listing);
            } catch (StaleElementReferenceException e) {
                System.out.println("Prosli jsme vsemi nabidkami.");
                break;
            } catch (Exception e) {
                System.out.println("Chyba behem analyzy nabidky: " + e.getMessage());
                continue;

            }

            //driver.quit();

            // Сохранение данных в JSON
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("airbnb_results.json")) {
                gson.toJson(listings, writer);
            }
        }


    }
}
