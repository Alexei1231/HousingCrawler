package model;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirbnbCrawler {
    private EdgeDriver driver;
    private WebDriverWait wait;


    public AirbnbCrawler() {
        this.driver = new EdgeDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public void crawl() throws InterruptedException, IOException {//hlavni metoda, ktera otevira stranku vyhledavani nabidek
        //a pak hleda informace o kartickach, co se na ni nachazi, pak uz pracuje s jednotlivymni kartickami (otevira
        // kazdou a hleda informaci)
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

                    //cena za noc
                    try {
                        WebElement priceElem = card.findElement(By.xpath(".//div[@aria-hidden='true']//span[contains(text(), '€')]"));
                        ;
                        String text = priceElem.getText(); // "734 €"
                        String clean = text.replaceAll("[^0-9]", "");
                        double pricePerNight = Double.parseDouble(clean);
                        listing.setPricePerNightEur(pricePerNight);

                    } catch (Exception e) {
                        System.out.println("⚠️ Cena za noc nenalezena: " + e.getMessage());
                    }

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


                    // Odkaz na detail nabidky
                    String href = card.findElement(By.cssSelector("a[href*='/rooms/']")).getAttribute("href");
                    listing.setUrl(href);
                    crawlFromCard(driver, listing, href); //vyhledavani dalsich informaci o nabidce oteviranim jeji karticky
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

        // Otevři novou záložku přes JavaScript
        ((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", url);

        // Přepni na novou záložku
        Set<String> allWindows = driver.getWindowHandles();
        for (String windowHandle : allWindows) {
            if (!windowHandle.equals(originalWindow)) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            try {
                // Pokus o zavření případného popup okna
                WebElement closePopupButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[aria-label='Zavřít']")));
                closePopupButton.click();
                Thread.sleep(1500);
            } catch (TimeoutException | NoSuchElementException ignored) {
            }

            // Popis nabídky
            WebElement descElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-section-id='DESCRIPTION_DEFAULT']")));
            String description = descElement.getText().trim();
            listing.setDescription(description);

            // Získání check-in data
            try {
                WebElement checkInElement = driver.findElement(By.cssSelector("div[data-testid='change-dates-checkIn']"));
                String checkInDate = checkInElement.getText().trim();
                listing.setCheckInDate(checkInDate);
            } catch (NoSuchElementException e) {
                System.out.println("Check-in datum nebylo nalezeno.");
            }


            // Získání info položek
            List<WebElement> infoItems = driver.findElements(By.cssSelector("ol.lgx66tx li"));

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

            //Host info (ze stejné stránky)
            Host host = new Host();


            crawlHost(driver, host);
            listing.setHost(host);


        } catch (Exception e) {
            System.out.println("Nepodařilo se získat popis pro: " + url);
        } finally {
            // Zavři záložku a vrať se zpět
            driver.close();
            driver.switchTo().window(originalWindow);
        }

    }


    public void crawlHost(WebDriver driver, Host host) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Sekce s hostitelem
            WebElement hostSection = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-section-id='MEET_YOUR_HOST']")));

            // Jméno hostitele
            try {
                WebElement nameElement = driver.findElement(By.xpath("//*[contains(text(),'Hostitelem je') or contains(text(),'Ubytuj se u')]"));
                String nameText = nameElement.getText().trim();
                String name = "";

                // Zjistit, jaká fráze byla použita, a extrahovat jméno
                if (nameText.startsWith("Hostitelem je")) {
                    name = nameText.replace("Hostitelem je", "").trim();
                } else if (nameText.startsWith("Ubytuj se u")) {
                    // Např. "Ubytuj se u hostitele Msm"
                    Pattern namePattern = Pattern.compile("Ubytuj se u\\s+(?:hostitele\\s+)?(.+)");
                    Matcher matcher = namePattern.matcher(nameText);
                    if (matcher.find()) {
                        name = matcher.group(1).trim();
                    }
                }

                if (!name.isEmpty()) {
                    host.setName(name);
                } else {
                    System.out.println("Nepodařilo se extrahovat jméno hostitele.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("Jméno hostitele nebylo nalezeno.");
            }

            // Text celé sekce pro analýzu
            String hostSectionText = hostSection.getText().toLowerCase();

            // Superhostitel
            host.setSuperhost(hostSectionText.contains("superhostitel"));

            // Délka hostování – např. "6 let hostí"
            Pattern pattern = Pattern.compile("(\\d+)\\s+(rok(?:y)?|let|měsíc(?:e)?|měsíce)\\s+hostí");
            Matcher matcher = pattern.matcher(hostSectionText);
            if (matcher.find()) {
                int number = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                if (unit.matches("(?i)rok(?:y|ů)?|let")) {
                    host.setHostingSince(String.valueOf(LocalDate.now().minusYears(number)));
                } else if (unit.matches("(?i)měsíc(?:e|ů)?")) {
                    host.setHostingSince(String.valueOf(LocalDate.now().minusMonths(number)));
                }

            }


            // ✅ Odpovědní index a doba reakce
            try {
                String fullText = hostSection.getText();

                // Robustní hledání: index odpovědí
                Pattern responseRatePattern = Pattern.compile("index\\s*odpovědí\\s*[:：]\\s*(\\d{1,3})\\s*%", Pattern.CASE_INSENSITIVE);
                Matcher responseRateMatcher = responseRatePattern.matcher(fullText);
                if (responseRateMatcher.find()) {
                    host.setResponseRate(responseRateMatcher.group(1));
                }

                // Robustní hledání: doba reakce
                Pattern responseTimePattern = Pattern.compile("reaguje během ([^\\n]+)", Pattern.CASE_INSENSITIVE);
                Matcher responseTimeMatcher = responseTimePattern.matcher(fullText);
                if (responseTimeMatcher.find()) {
                    String responseTime = "Reaguje během " + responseTimeMatcher.group(1).trim();
                    host.setResponseTime(responseTime);
                }

            } catch (Exception e) {
                System.out.println("Nepodařilo se získat odpovědní informace o hostiteli z textu.");
                e.printStackTrace();
            }


            // ✅ URL profilu z <a>
            try {
                // Najdi <a> element s atributem aria-label přesně podle zadání
                WebElement link = driver.findElement(By.xpath("//a[@aria-label='Přejít na celý profil hostitele']"));
                String href = link.getAttribute("href");

                if (href != null) {
                    if (!href.startsWith("http")) {
                        href = "https://www.airbnb.com" + href;
                    }
                    host.setProfileUrl(href);
                }
            } catch (NoSuchElementException e) {
                System.out.println("❌ Nebyl nalezen odkaz s aria-label='Přejít na celý profil hostitele'.");
            }


            //pocet recenzi a prumerne hodnoceni
            try {
                // Najdi počet recenzí – např. "255 hodnocení"
                Pattern reviewsPattern = Pattern.compile("(\\d+)\\s*hodnocení");
                Matcher reviewsMatcher = reviewsPattern.matcher(hostSectionText);
                if (reviewsMatcher.find()) {
                    int reviewCount = Integer.parseInt(reviewsMatcher.group(1));
                    host.setReviewCount(reviewCount);
                }

                // Najdi průměrné hodnocení – např. "průměrné hodnocení 4,64 z 5"
                Pattern ratingPattern = Pattern.compile("průměrné hodnocení\\s*([0-9.,]+)");
                Matcher ratingMatcher = ratingPattern.matcher(hostSectionText);
                if (ratingMatcher.find()) {
                    double rating = Double.parseDouble(ratingMatcher.group(1).replace(",", "."));
                    host.setAverageRating(rating);
                }
            } catch (Exception e) {
                System.out.println("Nepodařilo se získat průměrné hodnocení nebo počet hodnocení hostitele:");
                e.printStackTrace();
            }

            //zda je hostitel jednotlivec anebo firma
            try {
                WebElement hostTypeButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//button[contains(text(), 'Tuto nabídku nabízí')]"))
                );
                String hostText = hostTypeButton.getText().toLowerCase();

                // Určení typu hostitele
                if (hostText.contains("jednotlivec")) {
                    host.setProfessional(false);
                } else if (hostText.contains("firma")) {
                    host.setProfessional(true);
                }
            } catch (TimeoutException | NoSuchElementException e) {
                System.out.println("Nepodařilo se zjistit typ hostitele.");
            }


        } catch (Exception e) {
            System.out.println("Nepodařilo se získat údaje o hostiteli.");
            e.printStackTrace();
        }
        numberOfListingsSearch(driver, host, host.getProfileUrl());

    }

    //hledani poctu nabidek
//    public void numberOfListingsSearch(HashSet<Host> hosts, List<Listing> listings){
//       for(Listing listing : listings){
//           for(Host host : hosts){
//               if(host.equals(listing.getHost())){
//                   listing.getHost().increaseNumberOfListings();
//               }
//           }
//       }
//    }

    //hledani poctu nabidek se vstupem do karticky hostitele - nefungunguje protoe link na toho hosta neni z nejakeho duvod pristupny
    public void numberOfListingsSearch(WebDriver driver, Host host, String url) {
        String originalWindow = driver.getWindowHandle();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.open()");
        List<String> tabs = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(tabs.get(tabs.size() - 1));

        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            int numberOfListings = -1;

            try {
                // Pokus o nalezení tlačítka s textem „Zobrazit všechny uživatelovy nabídky (XX)“
                WebElement button = wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//button[contains(text(), 'Zobrazit všechny uživatelovy nabídky')]")
                ));
                String buttonText = button.getText();
                Matcher matcher = Pattern.compile("\\((\\d+)\\)").matcher(buttonText);
                if (matcher.find()) {
                    numberOfListings = Integer.parseInt(matcher.group(1));
                    System.out.println("Počet inzerátů z tlačítka: " + numberOfListings);
                }
            } catch (TimeoutException e) {
                System.out.println("Tlačítko nebylo nalezeno, zkouším spočítat odkazy.");
            }

            // Pokud tlačítko nebylo nalezeno nebo neobsahovalo číslo
            if (numberOfListings == -1) {
                List<WebElement> roomLinks = driver.findElements(By.xpath(
                        "//a[contains(@href, '/rooms/') and contains(@href, '?source_impression_id')]"
                ));
                numberOfListings = roomLinks.size();
                System.out.println("Počet inzerátů z odkazů: " + numberOfListings);
            }

            host.setNumberOfListings(numberOfListings);

        } catch (Exception e) {
            System.out.println("Chyba při získávání počtu nabídek hostitele: " + e.getMessage());
        } finally {
            driver.close();
            driver.switchTo().window(originalWindow);
        }
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