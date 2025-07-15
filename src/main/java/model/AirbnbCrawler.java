package model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openqa.selenium.*;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        List<Listing> listings = new ArrayList<>();
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
                        WebElement priceElem = card.findElement(By.xpath(".//div[@aria-hidden='true']//span[contains(text(), 'Kč')]"));
                        String text = priceElem.getText(); // "734 Kč"
                        String clean = text.replaceAll("[^0-9]", "");
                        double pricePerNight = Double.parseDouble(clean);
                        listing.setPricePerNight(pricePerNight);
                    } catch (Exception e) {
                        System.out.println("⚠️ Cena za noc nenalezena: " + e.getMessage());
                    }
//                    // Celková cena
//                    List<WebElement> totalPriceElems = card.findElements(By.cssSelector("span[aria-hidden='true']"));
//                    for (WebElement elem : totalPriceElems) {
//                        String text = elem.getText();
//                        if (text.contains("Kč")) {
//                            listing.setTotalPrice(Long.parseLong(text));
//                            break;
//                        }
//                    }

                    // Hodnocení a počet recenzí
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


                    //Počet měsíců hostování - доделать
                    //String monthsText = card.findElement(By.cssSelector("div[aria-label*='Months hosting'] > div")).getText();
                    //listing.setMonthsHosting(Integer.parseInt(monthsText));

                    // Superhost / Business host kontrola podle textu
//                    String html = card.getAttribute("innerHTML");
//                    listing.setSuperhost(html.contains("Superhost"));
//                    listing.setBusinessHost(html.contains("Business host"));

                    // Odkaz na detail nabídky
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
                WebElement closePopupButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("button[aria-label='Zavřít']"))
                );
                closePopupButton.click();
                Thread.sleep(300);
            } catch (TimeoutException | NoSuchElementException ignored) {
            }

            // Popis nabídky
            WebElement descElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-section-id='DESCRIPTION_DEFAULT']"))
            );
            String description = descElement.getText().trim();
            listing.setDescription(description);

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
//            Host host = new Host();
//
//            host.setAverageRating(listing.getRating());
//            host.setReviewCount(listing.getReviewCount());
//
//            crawlHost(driver, host);
//            listing.setHost(host);


        } catch (Exception e) {
            System.out.println("Nepodařilo se získat popis pro: " + url);
        } finally {
            // Zavři záložku a vrať se zpět
            driver.close();
            driver.switchTo().window(originalWindow);
        }
    }



//    public void crawlHost(WebDriver driver, Host host) {
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
//
//        try {
//            // Najdi kontejner s hostitelem
//            WebElement hostSection = wait.until(ExpectedConditions.presenceOfElementLocated(
//                    By.cssSelector("[data-section-id='HOST_OVERVIEW_DEFAULT']")));
//
//            //Jmeno hostitele
//            WebElement nameElement = driver.findElement(By.xpath("//*[contains(text(),'Hostitelem je')]"));
//            String name = nameElement.getText().replace("Hostitelem je", "").trim();
//            host.setName(name);
//
//            // Superhostitel
//            String hostSectionText = hostSection.getText().toLowerCase();
//            host.setSuperhost(hostSectionText.contains("superhostitel"));
//
//            // Hosting since (např. "6 let hostí")
//            Pattern pattern = Pattern.compile("(\\d+)\\s+let\\s+hostí");
//            Matcher matcher = pattern.matcher(hostSectionText);
//            if (matcher.find()) {
//                int years = Integer.parseInt(matcher.group(1));
//                host.setHostingSince(String.valueOf(LocalDate.now().minusYears(years)));
//            }
//
//            // Profilový odkaz (obvykle na <button> je `aria-label` s jménem, ale není <a>)
//            // Pokud chceš url profilu, můžeš si vytvořit vlastní z user ID (z URL obrázku):
//            WebElement img = hostSection.findElement(By.tagName("img"));
//            String imgUrl = img.getAttribute("src");
//            Pattern idPattern = Pattern.compile("/user/([a-f0-9\\-]+)\\.jpg");
//            Matcher idMatcher = idPattern.matcher(imgUrl);
//            if (idMatcher.find()) {
//                String userId = idMatcher.group(1);
//                host.setProfileUrl("https://www.airbnb.com/users/show/" + userId);
//            }
//
//        } catch (Exception e) {
//            System.out.println("Nepodařilo se získat údaje o hostiteli.");
//            e.printStackTrace();
//        }
//    }

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