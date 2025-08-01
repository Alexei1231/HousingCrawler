package model;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirbnbPriceCrawler {
    private EdgeDriver driver;
    private WebDriverWait wait;


    public AirbnbPriceCrawler() {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public void crawl(int maxThreads) throws InterruptedException, IOException {
        String searchUrl = "https://www.airbnb.cz/s/Praha/homes?currency=EUR&checkin=2025-10-25&checkout=2025-10-26";
        driver.get(searchUrl);
        Thread.sleep(5000);

        List<Listing> listings = new ArrayList<>();
        int pageNum = 2;

        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);

        while (true) {
            Thread.sleep(3000);

            List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid='card-container']"));
            System.out.println("Na strance " + (pageNum - 1) + " se naslo tolik nabidek: " + cards.size());

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
            } catch (InterruptedException e) {
                System.out.println("Přerušeno při čekání na dokončení úloh");
                executor.shutdownNow();
                throw e;
            }

            // Prechod na pristi stranku
//            List<WebElement> buttons = driver.findElements(By.xpath("//a[text()='" + pageNum + "']"));
//            if (!buttons.isEmpty()) {
//                try {
//                    buttons.get(0).click();
//                    pageNum++;
//                } catch (Exception e) {
//                    System.out.println("Chyba pri kliknuti na dalsi stranku");
//                    break;
//                }
//            } else {
//                System.out.println("Posledni stranka, ukonceni.");
//                break;
//            }
            break;
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        // Сохраняем результат
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("airbnb_results.json")) {
            gson.toJson(listings, writer);
            System.out.println("Ulozeno " + listings.size() + " nabidek do JSON.");
        }

        driver.quit();
    }


    public void crawlFromCard(Listing listing, String url) {
        // Настройка EdgeDriver
        EdgeOptions options = new EdgeOptions();
        //options.addArguments("--headless=new"); // Можно убрать headless, если хочешь видеть окна
        WebDriver localDriver = new EdgeDriver(options);
        WebDriverWait wait = new WebDriverWait(localDriver, Duration.ofSeconds(10));
        localDriver.get(url);
        try {

            // Подождем немного, чтобы убедиться, что страница загружена
            Thread.sleep(3000);

            try {
                WebElement closePopupButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[aria-label='Zavřít']")));
                closePopupButton.click();
                System.out.println("Pop-up byl zavren");
            } catch (TimeoutException e) {
                System.out.println("Pop-up nebyl nalezen");
            }

            try {
                WebElement acceptButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[normalize-space()='Pouze nezbytné']")));
                acceptButton.click();
                System.out.println("✅ Tlacitko 'Pouze nezbytne' bylo stisknute.");
            } catch (Exception e) {
                System.out.println("⚠️ Tlacitko 'Pouze nezbytné' nebylo nalezeno: " + e.getMessage());
            }


            // Тут позже будет логика парсинга
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d. M. yyyy");
            LocalDate startDate = LocalDate.now().plusDays(1); // Zitra
            LocalDate endDate = startDate.plusDays(100);//v production ready verzi je potreba nastavit 365

            for (LocalDate checkInDate = startDate; checkInDate.isBefore(endDate); checkInDate = checkInDate.plusDays(1)) {
                LocalDate checkOutDate = checkInDate.plusDays(1);
                String checkInStr = checkInDate.format(formatter);
                String checkOutStr = checkOutDate.format(formatter);

                try {
                    boolean success = setDates(localDriver, checkInStr, checkOutStr);


                    if (!success) {
                        System.out.println("Preskocili jsme datum: " + checkInStr);
                        continue; // идем к следующей дате
                    }
                    Thread.sleep(1000);
                    // Hledame <span>, jenz obsahuje cenu za noc
                    WebElement pricePerNightElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//span[contains(text(), 'za noc')]")));

                    //zbirame cenu
                    String rawText = pricePerNightElement.getText(); // например "€ 55 za noc"

                    //nechavame pouze jeji cislovou cast a davame je do tridy
                    String priceText = rawText.replaceAll("[^\\d,]", "").replace(",", ".");
                    double pricePerNight = Double.parseDouble(priceText);
                    Listing.Price price = new Listing.Price(java.sql.Date.valueOf(checkInDate), pricePerNight);
                    listing.addPrice(price);
                    //TODO: thread sleep for 500ms, then addprices etc - partly done, needs testing
                    System.out.println("Datum byl uspesne zpracovan: " + checkInStr + " → " + checkOutStr);
                } catch (Exception e) {
                    System.out.println("Datum nebul zpracovan " + checkInStr + ": " + e.getMessage());
                }
            }


        } catch (Exception e) {
            System.out.println("Chyba behem otevirani listingu: " + e.getMessage());
        } finally {
            // Закрываем драйвер
            localDriver.quit();
            System.out.println("Driver byl zavren pro listing: " + listing.getTitle());
        }
    }


    public void clickCalendar(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            WebElement calendarToggle = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='change-dates-checkIn']")
            ));

            // Прокрутка к элементу перед кликом
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", calendarToggle);
            Thread.sleep(300); // немножко подождать после скролла

            calendarToggle.click();

            // Ждём, пока появится сам календарь
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='bookit-sidebar-availability-calendar']")
            ));

            System.out.println("✔ Календарь открыт");

        } catch (ElementClickInterceptedException e) {
            System.out.println("⚠ Не удалось кликнуть по элементу открытия календаря: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("⚠ Ошибка при попытке открыть календарь: " + e.getMessage());
        }
    }


    public boolean setDates(WebDriver driver, String checkIn, String checkOut) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
        try {
            clickCalendar(driver);
        } catch (Exception ignored) {
        }
        try {
            // чек-ин
            WebElement checkInInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("checkIn-book_it")));
            checkInInput.click();
            checkInInput.sendKeys(Keys.CONTROL + "a");
            checkInInput.sendKeys(Keys.BACK_SPACE);
            checkInInput.sendKeys(checkIn);
            checkInInput.sendKeys(Keys.ENTER);

            if (isDateUnavailable(driver)) {
                return false;
            }
            // чек-аут
            WebElement checkOutInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("checkOut-book_it")));
            checkOutInput.click();
            checkOutInput.sendKeys(Keys.CONTROL + "a");
            checkOutInput.sendKeys(Keys.BACK_SPACE);
            checkOutInput.sendKeys(checkOut);
            checkOutInput.sendKeys(Keys.ENTER);

            if (isDateUnavailable(driver)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            System.out.println("Nelze aplikovat daty: " + checkIn + " a " + checkOut + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isDateUnavailable(WebDriver driver) {
        try {
            WebElement errorDiv = driver.findElement(By.id("book_it_dateInputsErrorId"));
            return errorDiv.isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }


}