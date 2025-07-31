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
        String searchUrl = "https://www.airbnb.cz/s/Praha/homes?currency=EUR";
        driver.get(searchUrl);
        Thread.sleep(5000);

        List<Listing> listings = new ArrayList<>();
        HashSet<Host> hosts = new HashSet<>();
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

            // Запускаем все задачи и ждём их окончания
            try {
                List<Future<Void>> futures = executor.invokeAll(tasks);
                // Можно пройтись по futures и проверить исключения, если надо
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

            // Переход на следующую страницу
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
                System.out.println("ℹ Попап закрыт");
            } catch (TimeoutException e) {
                System.out.println("ℹ Попап не найден");
            }

            // Тут позже будет логика парсинга
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d. M. yyyy");
            LocalDate startDate = LocalDate.now().plusDays(1); // Завтра
            LocalDate endDate = startDate.plusDays(365);

            for (LocalDate checkInDate = startDate; checkInDate.isBefore(endDate); checkInDate = checkInDate.plusDays(1)) {
                LocalDate checkOutDate = checkInDate.plusDays(1);
                String checkInStr = checkInDate.format(formatter);
                String checkOutStr = checkOutDate.format(formatter);

                try {
                    boolean success = setDates(localDriver, checkInStr, checkOutStr);

                    if (!success) {
                        System.out.println("⏭ Пропускаем дату: " + checkInStr);
                        continue; // идем к следующей дате
                    }
                    Thread.sleep(300);
                    //TODO: thread sleep for 500ms, then addprices etc
                    System.out.println("✔ Дата установлена: " + checkInStr + " → " + checkOutStr);
                } catch (Exception e) {
                    System.out.println("⚠ Ошибка при дате " + checkInStr + ": " + e.getMessage());
                }
            }


        } catch (Exception e) {
            System.out.println("✖ Ошибка при открытии листинга: " + e.getMessage());
        } finally {
            // Закрываем драйвер
            localDriver.quit();
            System.out.println("✖ Закрыт драйвер для листинга: " + listing.getTitle());
        }
    }


    private void resetPageState(WebDriver driver) {
        try {
            // Попробуем закрыть календарь кликом по пустому месту
            Actions actions = new Actions(driver);
            actions.moveByOffset(10, 10).click().perform();
            Thread.sleep(1000);
        } catch (Exception ignored) {
        }

        try {
            // Попробуем нажать Escape
            new Actions(driver).sendKeys(Keys.ESCAPE).perform();
            Thread.sleep(500);
        } catch (Exception ignored) {
        }

        try {
            // JavaScript: сброс фокуса
            ((JavascriptExecutor) driver).executeScript("document.activeElement.blur();");
        } catch (Exception ignored) {
        }
    }

    private void clickCalendar(WebDriver driver) {
        WebElement checkInElement = driver.findElement(By.cssSelector("div[data-testid='change-dates-checkIn']"));
        checkInElement.click();
    }

    public boolean setDates(WebDriver driver, String checkIn, String checkOut) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
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

            // чек-аут
            WebElement checkOutInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("checkOut-book_it")));
            checkOutInput.click();
            checkOutInput.sendKeys(Keys.CONTROL + "a");
            checkOutInput.sendKeys(Keys.BACK_SPACE);
            checkOutInput.sendKeys(checkOut);
            checkOutInput.sendKeys(Keys.ENTER);

            return true;
        } catch (Exception e) {
            System.out.println("⚠ Невозможно установить даты " + checkIn + " → " + checkOut + ": " + e.getMessage());
            return false;
        }
    }


    // Допоміжний метод для парсингу ціни (замінює кому на крапку)
    private double parseDoublePrice(String text) {
        String clean = text.replaceAll("[^0-9,]", "").replace(",", ".");
        return Double.parseDouble(clean);
    }


}