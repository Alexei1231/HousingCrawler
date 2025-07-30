package model;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

        // Создаём executor с одним потоком (можно будет изменить на 18)
        ExecutorService executor = Executors.newFixedThreadPool(1);

        while (true) {
            Thread.sleep(3000);

            List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid='card-container']"));
            System.out.println("Na strance " + (pageNum - 1) + " se naslo tolik nabidek: " + cards.size());

            for (WebElement card : cards) {
                try {
                    // Название и ссылка
                    String title = card.findElement(By.cssSelector("[data-testid='listing-card-title']")).getText();
                    String href = card.findElement(By.cssSelector("a[href*='/rooms/']")).getAttribute("href");

                    Listing listing = new Listing(title);
                    listings.add(listing);

                    // Запуск асинхронного потока
                    executor.submit(() -> {
                        crawlFromCard(listing, href + "?locale=cs&currency=EUR"); // теперь crawlFromCard сам создаёт WebDriver
                    });

                } catch (Exception e) {
                    System.out.println("Chyba pri zpracovani karty: " + e.getMessage());
                }
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

        // Завершаем потоки
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        // Сохраняем результат
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("airbnb_results.json")) {
            gson.toJson(listings, writer);
            System.out.println("Ulozeno " + listings.size() + " nabidek do JSON.");
        }
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
                    clickCalendar(localDriver);  // открыть календарь
                    setDates(localDriver, checkInStr, checkOutStr);  // ввести даты
                    //String price = extractPrice(localDriver); // TODO: метод, который собирает цену с экрана
                    //listing.getPriceArrayList().add(price); // добавляем цену к листингу

                    //System.out.println("✔ Дата: " + checkInStr + " → Цена: " + price);
                } catch (Exception e) {
                    System.out.println("⚠ Ошибка при обработке даты " + checkInStr + ": " + e.getMessage());
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

    public void setDates(WebDriver driver, String checkIn, String checkOut) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Встановлюємо дату приїзду
        WebElement checkInInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("checkIn-book_it")));
        checkInInput.click();
        checkInInput.sendKeys(Keys.CONTROL + "a"); // Виділити стару дату
        checkInInput.sendKeys(Keys.BACK_SPACE);    // Видалити
        checkInInput.sendKeys(checkIn);
        checkInInput.sendKeys(Keys.ENTER);         // Підтвердити

        // Встановлюємо дату від’їзду
        WebElement checkOutInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("checkOut-book_it")));
        checkOutInput.click();
        checkOutInput.sendKeys(Keys.CONTROL + "a");
        checkOutInput.sendKeys(Keys.BACK_SPACE);
        checkOutInput.sendKeys(checkOut);
        checkOutInput.sendKeys(Keys.ENTER);
    }



    // Допоміжний метод для парсингу ціни (замінює кому на крапку)
    private double parseDoublePrice(String text) {
        String clean = text.replaceAll("[^0-9,]", "").replace(",", ".");
        return Double.parseDouble(clean);
    }


}