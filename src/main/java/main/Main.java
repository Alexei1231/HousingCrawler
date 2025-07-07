package main;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.google.gson.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;


public class Main {
    static class Listing {
        String title;
        //String price;
        String link;

        public Listing(String title, String link) {
            this.title = title;
            //this.price = price;
            this.link = link;
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Napiste mesto v anglictine (napr. Prague, Brno): ");
        String city = scanner.nextLine().trim();

        // HTML forming
        String searchUrl = "https://www.booking.com/searchresults.html?ss=" +
                URLEncoder.encode(city, StandardCharsets.UTF_8);

        // Driver
        WebDriver driver = new EdgeDriver();
        driver.get(searchUrl);

        //Page loading
        Thread.sleep(5000);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
            //Automatic scrolling and buttonpressing
        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(5000); // waiting until loaded

            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == lastHeight) {
                // Trying to find "Load More Results button"
                List<WebElement> buttons = driver.findElements(By.xpath("//button[span[text()='Load more results']]"));
                if (!buttons.isEmpty()) {
                    try{
                    buttons.get(0).click();
                    }
                    catch(Exception e){
                        break;
                    }
                    try {
                        Thread.sleep(2000);
                    }catch(Exception e){
                        Thread.sleep(5000);
                    }
                } else {
                    System.out.println("Prosli jsme vsemi nabidkami. Zacina se ulozeni do souboru");
                    break;
                }
            }

            lastHeight = newHeight;
        }

        // Accepting cards with offers
        List<WebElement> cards = driver.findElements(By.cssSelector("div[data-testid=\"property-card\"]"));
        List<Listing> results = new ArrayList<>();

        for (WebElement card : cards) {
            try {
                String title = card.findElement(By.cssSelector("[data-testid=\"title\"]")).getText();
                //String price = card.findElement(By.cssSelector("[data-testid=\"price-and-discounted-price\"]")).getText();
String url = card.findElement(By.cssSelector("[data-testid=\"title-link\"]")).getAttribute("href");

                results.add(new Listing(title, url));
            } catch (Exception e) {
                continue;
            }
        }

        driver.quit();

        // Saving with GSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("booking_results.json")) {
            gson.toJson(results, writer);
        }

        System.out.println("Hotovo! Mame " + results.size() + " nabidek v booking_results.json");


        Reader reader = new FileReader("booking_results.json");
        JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();

        // Создание Excel файла
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Listings");

        // Запись заголовков
        Row headerRow = sheet.createRow(0);
        Set<String> headers = new LinkedHashSet<>();

        // Соберём заголовки из первого элемента
        if (jsonArray.size() > 0) {
            JsonObject firstObj = jsonArray.get(0).getAsJsonObject();
            headers.addAll(firstObj.keySet());
        }

        int colIdx = 0;
        for (String header : headers) {
            headerRow.createCell(colIdx++).setCellValue(header);
        }

        // Запись данных
        int rowIdx = 1;
        for (JsonElement elem : jsonArray) {
            JsonObject obj = elem.getAsJsonObject();
            Row row = sheet.createRow(rowIdx++);
            colIdx = 0;
            for (String header : headers) {
                Cell cell = row.createCell(colIdx++);
                JsonElement value = obj.get(header);
                if (value != null && !value.isJsonNull()) {
                    cell.setCellValue(value.getAsString());
                }
            }
        }

        // Сохранение
        FileOutputStream out = new FileOutputStream("output.xlsx");
        workbook.write(out);
        out.close();
        workbook.close();

        System.out.println("Excel файл успешно создан: output.xlsx");
    }


}
