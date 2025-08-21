package model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
    private int waitingPeriod;
    private int numberOfThreads;

    public AirbnbPriceCrawler(int numberOfThreads, int waitingPeriod) {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        this.waitingPeriod = waitingPeriod;
        this.numberOfThreads = numberOfThreads;
    }

    public void crawl(String url) throws InterruptedException, IOException {
        String searchUrl = url;
        driver.get(searchUrl);
        Thread.sleep(5000);

        List<Listing> listings = new ArrayList<>();
        int pageNum = 2;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

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
                    listing.setUrl(href);
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



        // Ukladame vysledek do JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream("airbnb_results.json"), StandardCharsets.UTF_8)) {
            gson.toJson(listings, writer);
            System.out.println("Uloženo " + listings.size() + " nabídek do JSON.");
        } catch (IOException e) {
            e.printStackTrace();
        }


        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        driver.quit();
    }


    public void crawlFromCard(Listing listing, String url) {
        // Vytvoříme nový EdgeDriver pro paralelní zpracování
        EdgeOptions options = new EdgeOptions();
        //options.addArguments("--headless=new"); // odkomentuj, pokud chceš běžet bez GUI
        WebDriver localDriver = new EdgeDriver(options);
        WebDriverWait wait = new WebDriverWait(localDriver, Duration.ofSeconds(10));

        localDriver.get(url);

        try {

            try {
                // Pokus o zavření případného popup okna
                WebElement closePopupButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[aria-label='Zavřít']")));
                closePopupButton.click();
                Thread.sleep(1500);
            } catch (TimeoutException | NoSuchElementException ignored) {
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Popis nabídky
            WebElement descElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-section-id='DESCRIPTION_DEFAULT']")));
            String description = descElement.getText().trim();
            listing.setDescription(description);

            //!!!LOCATION!!!
//            WebElement locationDiv = wait.until(ExpectedConditions
//                    .visibilityOfElementLocated(By.cssSelector("div.s1qk96pm.atm_gq_p5ox87.dir.dir-ltr")));
//
//            listing.setLocation(locationDiv.getText());

            //!!!REVIEW COUNT AND AVERAGE REVIEW MARK!!!

//            WebElement ratingContainer = wait.until(ExpectedConditions
//                    .visibilityOfElementLocated(By.cssSelector("div.r1dxllyb.atm_gq_p5ox87.dir.dir-ltr")));
//
//            List<WebElement> ratingSpans = ratingContainer.findElements(By.cssSelector("span"));

// prumerne hodnoceni
//            double averageRating = 0.0;
//            if (!ratingSpans.isEmpty()) {
//                try {
//                    averageRating = Double.parseDouble(ratingSpans.get(0).getText().replace(",", "."));
//                } catch (NumberFormatException e) {
//                    System.out.println("Не удалось преобразовать рейтинг: " + ratingSpans.get(0).getText());
//                }
//            }

// pocet hodnoceni
//            int reviewCount = 0;
//            if (ratingSpans.size() > 1) {
//                String reviewText = ratingSpans.get(1).getText(); // например "(128 hodnocení)"
//                reviewText = reviewText.replaceAll("[^0-9]", ""); // оставляем только цифры
//                if (!reviewText.isEmpty()) {
//                    reviewCount = Integer.parseInt(reviewText);
//                }
//            }
//            listing.setRating(averageRating);
//            listing.setReviewCount(reviewCount);

            //!!!AMENITIES!!!
            List<WebElement> infoItems = wait.until(ExpectedConditions
                    .presenceOfAllElementsLocatedBy(By.cssSelector("ol.lgx66tx li")));


            int guests = 0;
            int bedrooms = 0;
            int beds = 0;
            int bathrooms = 0;

            for (WebElement item : infoItems) {
                String text = item.getText().toLowerCase().trim();

                if (text.contains("hosté") || text.contains("hostů")) {
                    Matcher matcher = Pattern.compile("(\\d+)\\s+host").matcher(text);
                    if (matcher.find()) {
                        guests = Integer.parseInt(matcher.group(1));
                    }
                }

                if (text.contains("ložnic")) {
                    Matcher matcher = Pattern.compile("(\\d+)\\s+ložnic").matcher(text);
                    if (matcher.find()) {
                        bedrooms = Integer.parseInt(matcher.group(1));
                    }
                } else if (text.contains("ložnice")) {
                    Matcher matcher = Pattern.compile("(\\d+)\\s+ložnice").matcher(text);
                    if (matcher.find()) {
                        bedrooms = Integer.parseInt(matcher.group(1));
                    }
                } else if (text.contains("studio")) {
                    bedrooms = 1;
                }

                if (text.matches(".*\\d+\\s+(postel|lůžk|manželská|jednolůžk|patrová|dvoulůžk|přistýlk).*")) {
                    Matcher matcher = Pattern.compile("(\\d+)\\s+(postel|lůžk|manželská|jednolůžk|patrová|dvoulůžk|přistýlk)").matcher(text.toLowerCase());
                    if (matcher.find()) {
                        beds = Integer.parseInt(matcher.group(1));
                    }
                }

                if (text.contains("koupelna")) {
                    if (text.contains("sdílená")) {
                        bathrooms = 0;
                    } else {
                        Matcher matcher = Pattern.compile("(\\d+)\\s+(soukromá\\s+)?koupelna").matcher(text);
                        if (matcher.find()) {
                            bathrooms = Integer.parseInt(matcher.group(1));
                        } else {
                            bathrooms = 1;
                        }
                    }
                }
            }

            listing.setMaxGuests(guests);
            listing.setBedrooms(bedrooms);
            listing.setBeds(beds);
            listing.setBathrooms(bathrooms);


        } catch (NoSuchElementException e) {
            System.out.println("Chyba behem zpracovani nabidky");
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
        }
        crawlPriceFromCard(listing, localDriver);
        localDriver.close();
    }

    public void crawlPriceFromCard(Listing listing, WebDriver driver) {
        // Настройка EdgeDriver
        //options.addArguments("--headless=new"); // Можно убрать headless, если хочешь видеть окна
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
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
                    boolean success = setDates(driver, checkInStr, checkOutStr);


                    if (!success) {
                        System.out.println("Preskocili jsme datum: " + checkInStr);
                        listing.addPrice(new Listing.Price(java.sql.Date.valueOf(checkInDate), -1));
                        continue; // идем к следующей дате
                    }
                    Thread.sleep(1500); //!!!ČEKÁNÍ NA ODPOVĚĎ SERVERU!!! TODO: UDĚLEJ NASTAVITELNÝ ČAS

                    // Hledame <span>, jenz obsahuje cenu za noc
                    String priceText = (String) ((JavascriptExecutor) driver).executeScript(
                            "var blocks = document.querySelectorAll('div._1k1ce2w');" +
                                    "for (var i = 0; i < blocks.length; i++) {" +
                                    "  var block = blocks[i];" +
                                    "  if (block.offsetParent !== null) {" + // видимый блок" +
                                    "    var matches = [];" +
                                    "    var spans = block.querySelectorAll('span');" +
                                    "    for (var j = 0; j < spans.length; j++) {" +
                                    "      var s = spans[j];" +
                                    "      if (s.offsetParent !== null) {" +
                                    "        var m = s.innerText.match(/€\\s*(\\d+[\\.,]?\\d*)/);" +
                                    "        if (m && m[1]) matches.push(m[1]);" +
                                    "      }" +
                                    "    }" +
                                    "    if (matches.length > 0) return matches[matches.length - 1];" + // последнее число" +
                                    "  }" +
                                    "}" +
                                    "return null;"
                    );

                    System.out.println("Cena za 1 noc: " + priceText);


                    if (priceText == null) {
                        System.out.println("Nepodarilo se ziskat cenu pro datum: " + checkInStr);
                        continue;
                    }

// Парсим цену
                    Pattern pattern = Pattern.compile("(\\d+[\\.,]?\\d*)");
                    Matcher matcher = pattern.matcher(priceText);
                    if (matcher.find()) {
                        double pricePerNight = Double.parseDouble(matcher.group(1).replace(",", "."));
                        listing.addPrice(new Listing.Price(java.sql.Date.valueOf(checkInDate), pricePerNight));
                        System.out.println("Datum byl uspesne ulozen: " + checkInStr + " , cena: " + pricePerNight);
                    } else {
                        System.out.println("Nepodarilo se parsovani data z JS: " + priceText);
                    }

                    System.out.println("Datum byl uspesne zpracovan: " + checkInStr + " → " + checkOutStr);
                } catch (Exception e) {
                    System.out.println("Datum nebyl zpracovan " + checkInStr + ": " + e.getMessage());
                }
            }


        } catch (Exception e) {
            System.out.println("Chyba behem otevirani listingu: " + e.getMessage());
        } finally {
            // Закрываем драйвер
            driver.quit();
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

            System.out.println("✔ Kalendar byl otevren");

        } catch (ElementClickInterceptedException e) {
            System.out.println("⚠ Nepodarilo se  clicknout na element kalendare - datum neni dostupny");
        } catch (Exception e) {
            System.out.println("⚠ Chyba pri pokusu otevreni kalendare, nejspise kvuli male cekaci lhute: " + e.getMessage());
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
            System.out.println("Nelze aplikovat data: " + checkIn + " a " + checkOut + ": " + e.getMessage());
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