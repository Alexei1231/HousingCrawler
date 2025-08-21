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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirbnbHostCrawler {
    private EdgeDriver driver;
    private WebDriverWait wait;
    private String url;//url to the host website
    private int numberOfThreads;
    private int waitingTime;
    private int numberOfDays;

    public AirbnbHostCrawler(String url, int numberOfDays, int numberOfThreads, int waitingTime) {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        url.trim();
        this.url = url;
        this.numberOfThreads = numberOfThreads;
        this.waitingTime = waitingTime;
        this.numberOfDays = numberOfDays;
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

        js.executeScript("window.scrollTo(0, 650)");//opustime se na strance do mista, kde se nachazeji nabidky

        //a tady budeme (podobne jako v metode crawl v tride AirbnbCrawler) pak pracovat i s jednotlivymi nabidkami metodou
        //initializujeme ArrayList pro hosta, ktery mu pak na konci "dame"
        ArrayList<Listing> listings = new ArrayList<>();


        WebElement showAllButton = null;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);//POCET VLAKEN

        try { //v tomto try catch bloku proverime, zda bude najdene tlacitko, ktere otevira vsechny nabidky; pokud ne, tak pracujeme
            // ryze s tim, co je na strance uzivatele
            showAllButton = new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath(".//button[starts-with(normalize-space(), 'Zobrazit všechny uživatelovy nabídky')]")
                    ));
            showAllButton.click();
            Thread.sleep(2000);

// pokud je nalezeno tlacitko "Zobrazit dalsi nabidky", tiskneme na nej
            while (true) {
                try {
                    WebElement moreButton = new WebDriverWait(driver, Duration.ofSeconds(2))
                            .until(ExpectedConditions.elementToBeClickable(
                                    By.xpath(".//button[normalize-space()='Zobrazit další nabídky']")
                            ));

                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", moreButton);
                    moreButton.click();
                    Thread.sleep(2000); // подождать, пока прогрузятся новые карточки
                } catch (TimeoutException e) {
                    break; // кнопка больше не появляется
                }
            }

            // Кdyz je vsechno hotovo, zbirame karticky
            List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid='card-container']"));
            System.out.println("Na strance se naslo tolik nabidek: " + cards.size());

            List<Callable<Void>> tasks = new ArrayList<>();

            for (WebElement card : cards) {
                try {
                    String title = card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText();
                    String href = card.findElement(By.cssSelector("a[href*='/rooms/']")).getAttribute("href");

                    Listing listing = new Listing(title);//vytvarime listing
                    listing.setUrl(href);
                    // Hodnocení a počet recenzí nabidky
                    try {
                        WebElement ratingSpan = card.findElement(By.xpath(".//span[@aria-hidden='true' and contains(text(), '(')]"));
                        String ratingText = ratingSpan.getText(); // např. "4,63 (17)"
                        String[] parts = ratingText.split("[\\s\\(\\)]"); // ["4,63", "", "17", ""]

                        String ratingStr = parts[0].replace(',', '.');  // změna čárky na tečku
                        double rating = Double.parseDouble(ratingStr);
                        int reviewsCount = Integer.parseInt(parts[2]);

                        listing.setRating(rating);
                        listing.setReviewCount(reviewsCount);
                    } catch (NoSuchElementException e) {
                        listing.setRating(0.0);
                        listing.setReviewCount(0);
                    }


                    listings.add(listing);
                    listing.setHost(host);

                    Callable<Void> task = () -> {
                        crawlFromCard(listing, href + "?locale=cs&currency=EUR");
                        return null;
                    };

                    tasks.add(task);

                } catch (Exception e) {
                    System.out.println("Chyba pri zpracovani karty: " + e.getMessage());
                }
            }

// zpustime vsechny vlakna
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

        } catch (TimeoutException e) {//pokud tlacitko nebude nalezeno, tak hledame listingy na strance uzivatele
            List<Callable<Void>> tasks = new ArrayList<>();
            Set<String> processedUrls = new HashSet<>(); // Для защиты от дублей

            List<WebElement> cards = driver.findElements(
                    By.xpath("//div[.//*[@data-testid='listing-card-title']]")
            );

            System.out.println("Na stránce se našlo tolik nabídek: " + cards.size());

            String currentPage = driver.getCurrentUrl(); // Чтобы избежать зацикливания на себе

            for (WebElement card : cards) {
                try {
                    String title = card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText();
                    String href = card.findElement(By.xpath(".//a[contains(@href, '/rooms/')]"))
                            .getAttribute("href");

                    // Ověřujeme, zdali nemáme na stránce duplikáty(to je možné, neboť struktura kartičky na hl. strance
                    // uživ. se liší od strn. na dalších stránkách)
                    if (processedUrls.contains(href) || href.equals(currentPage)) {
                        System.out.println("Přeskočeno duplicitní nebo stejná stránka: " + href);
                        continue;
                    }

                    processedUrls.add(href);

                    Listing listing = new Listing(title);
                    listing.setUrl(href);
                    listings.add(listing);


                    Callable<Void> task = () -> {
                        crawlFromCard(listing, href + "?locale=cs&currency=EUR");
                        return null;
                    };


                    tasks.add(task);

                } catch (Exception e1) {
                    System.out.println("Chyba při zpracování karty: " + e1.getMessage());
                }
            }

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


        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("airbnb_results.json")) {
            gson.toJson(listings, writer);
            System.out.println("Ulozeno " + listings.size() + " nabidek do JSON.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


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


            // Nastavujeme daty pro crawling
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d. M. yyyy");
            LocalDate startDate = LocalDate.now().plusDays(1); // Zitra
            LocalDate endDate = startDate.plusDays(numberOfDays);//v production ready verzi je potreba nastavit 365
            //tady prochazime kazdem datem
            for (LocalDate checkInDate = startDate; checkInDate.isBefore(endDate); checkInDate = checkInDate.plusDays(1)) {
                LocalDate checkOutDate = checkInDate.plusDays(1);
                String checkInStr = checkInDate.format(formatter);
                String checkOutStr = checkOutDate.format(formatter);

                try {
                    boolean success = setDates(driver, checkInStr, checkOutStr); //pokud nastaveni data bylo uspesne, pookracujeme


                    if (!success) {
                        System.out.println("Preskocili jsme datum: " + checkInStr);
                        listing.addPrice(new Listing.Price(java.sql.Date.valueOf(checkInDate), -1));
                        continue; // pokud nastaveni bylo neuspesne, jdema dal
                    }
                    Thread.sleep(waitingTime); //!!!ČEKÁNÍ NA ODPOVĚĎ SERVERU!!!

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


                    if (priceText == null) { // pokud cena null, jdeme dal
                        System.out.println("Nepodarilo se ziskat cenu pro datum: " + checkInStr);
                        continue;
                    }

// parsovani ceny do listingu, pokud byla nalezena
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


    public void clickCalendar(WebDriver driver) { //pomocna metoda clickajici po tlacitku check-inu, abychom otevrili kalendar
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            WebElement calendarToggle = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='change-dates-checkIn']")
            ));

            // scrollujeme k elementu, ktery chceme clicknout
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", calendarToggle);
            Thread.sleep(300); // немножко подождать после скролла

            calendarToggle.click();

            // cekame, az se ibjevi kalendar
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='bookit-sidebar-availability-calendar']")
            ));

            System.out.println("✔ Kalendar byl otevren");

        } catch (ElementClickInterceptedException e) {
            System.out.println("⚠ Nepodarilo se  clicknout na element kalendare - datum neni dostupny");
        } catch (Exception e) {
            System.out.println("⚠ Chyba pri pokusu otevreni kalendare, nejspise kvuli nizke cekaci lhute: " + e.getMessage());
        }
    }


    public boolean setDates(WebDriver driver, String checkIn, String checkOut) {//pomocna metoda nastaveni data
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
        try {
            clickCalendar(driver);//zkousime clicknout na kalendar
        } catch (Exception ignored) {
        }
        try {
            // check in
            WebElement checkInInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("checkIn-book_it")));
            checkInInput.click();
            checkInInput.sendKeys(Keys.CONTROL + "a");
            checkInInput.sendKeys(Keys.BACK_SPACE);
            checkInInput.sendKeys(checkIn);
            checkInInput.sendKeys(Keys.ENTER);

            if (isDateUnavailable(driver)) {
                return false;
            }
            // check out
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

    private boolean isDateUnavailable(WebDriver driver) { // pomocna metoda hledajici label jenz ukazuje na nedostupnost data
        try {
            WebElement errorDiv = driver.findElement(By.id("book_it_dateInputsErrorId"));
            return errorDiv.isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

}