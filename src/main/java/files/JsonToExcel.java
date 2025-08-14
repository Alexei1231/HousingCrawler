package files;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class JsonToExcel {

    public static void xlsxConverter(String fileName) throws IOException {
        Reader reader = new FileReader(fileName);
        JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Listings");

        Set<String> headers = new LinkedHashSet<>();

        // Vytvořme sadu všech možných klíčů přes flattening
        List<Map<String, String>> flattenedData = new ArrayList<>();
        for (JsonElement elem : jsonArray) {
            Map<String, String> flat = new LinkedHashMap<>();
            flattenJson("", elem.getAsJsonObject(), flat);
            flattenedData.add(flat);
            headers.addAll(flat.keySet()); // sběr všech klíčů
        }

        // Zapsání hlavičky
        Row headerRow = sheet.createRow(0);
        int colIdx = 0;
        List<String> headerList = new ArrayList<>(headers);
        for (String header : headerList) {
            headerRow.createCell(colIdx++).setCellValue(header);
        }

        // Zápis dat
        int rowIdx = 1;
        for (Map<String, String> flatRow : flattenedData) {
            Row row = sheet.createRow(rowIdx++);
            colIdx = 0;
            for (String header : headerList) {
                Cell cell = row.createCell(colIdx++);
                String value = flatRow.get(header);
                if (value != null) {
                    try {
                        if (value.matches("-?\\d+(\\.\\d+)?")) {
                            cell.setCellValue(Double.parseDouble(value));
                        } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                            cell.setCellValue(Boolean.parseBoolean(value));
                        } else {
                            cell.setCellValue(value);
                        }
                    } catch (Exception e) {
                        cell.setCellValue(value); // fallback jako text
                    }
                }
            }
        }

        // Auto size
        for (int i = 0; i < headerList.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        // Uložit
        FileOutputStream out = new FileOutputStream("output_bnb.xlsx");
        workbook.write(out);
        out.close();
        workbook.close();

        System.out.println("✅ Excel soubor byl úspěšně vytvořen: output_bnb.xlsx");
    }

    private static void flattenJson(String prefix, JsonObject jsonObj, Map<String, String> flat) {
        for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement val = entry.getValue();

            if (val.isJsonPrimitive()) {
                flat.put(key, val.getAsJsonPrimitive().getAsString());
            } else if (val.isJsonObject()) {
                flattenJson(key, val.getAsJsonObject(), flat);
            } else if (val.isJsonNull()) {
                flat.put(key, "");
            } else if (val.isJsonArray()) {
                flat.put(key, val.toString()); // Array jako text
            }
        }
    }

    public void jsonToExcelForPrices(String jsonFilePath) throws IOException {
        // Читаем JSON с помощью Gson
        String excelFilePath = "bnb_listings.xlsx";
        Gson gson = new Gson();
        Reader reader = new FileReader(jsonFilePath);
        Type listType = new TypeToken<List<Map<String, Object>>>() {
        }.getType();
        List<Map<String, Object>> listings = gson.fromJson(reader, listType);
        reader.close();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Listings");

            // Заголовок
            Row headerRow = sheet.createRow(0);
            int headerCol = 0;
            headerRow.createCell(headerCol++).setCellValue("Title");
            headerRow.createCell(headerCol++).setCellValue("Location");

            // Собираем уникальные даты с ценами
            Set<String> allDates = new LinkedHashSet<>();
            for (Map<String, Object> listing : listings) {
                List<Map<String, Object>> prices = (List<Map<String, Object>>) listing.get("priceArrayList");
                if (prices != null) {
                    for (Map<String, Object> priceEntry : prices) {
                        if (priceEntry.get("price") != null) {
                            allDates.add((String) priceEntry.get("date"));
                        }
                    }
                }
            }

            List<String> dateList = new ArrayList<>(allDates);
            for (String date : dateList) {
                headerRow.createCell(headerCol++).setCellValue(date);
            }

            // Данные
            int rowIdx = 1;
            for (Map<String, Object> listing : listings) {
                Row row = sheet.createRow(rowIdx++);
                int colIdx = 0;

                // Название и локация
                row.createCell(colIdx++).setCellValue((String) listing.get("title"));
                row.createCell(colIdx++).setCellValue((String) listing.get("location"));

                // Цены в Map для быстрого поиска
                Map<String, Double> priceMap = new HashMap<>();
                List<Map<String, Object>> prices = (List<Map<String, Object>>) listing.get("priceArrayList");
                if (prices != null) {
                    for (Map<String, Object> priceEntry : prices) {
                        String date = (String) priceEntry.get("date");
                        Double price = (priceEntry.get("price") instanceof Number)
                                ? ((Number) priceEntry.get("price")).doubleValue()
                                : null;
                        if (price != null) {
                            priceMap.put(date, price);
                        }
                    }
                }

                // Заполняем цены по датам
                for (String date : dateList) {
                    if (priceMap.containsKey(date)) {
                        row.createCell(colIdx++).setCellValue(priceMap.get(date));
                    } else {
                        row.createCell(colIdx++).setBlank(); // недоступно → пусто
                    }
                }
            }

            // Авторазмер
            for (int i = 0; i < headerCol; i++) {
                sheet.autoSizeColumn(i);
            }

            // Сохраняем файл
            FileOutputStream out = new FileOutputStream(excelFilePath);
            workbook.write(out);
            out.close();

            System.out.println("✅ Excel успешно создан: " + excelFilePath);
        }
    }
}
