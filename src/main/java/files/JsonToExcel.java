package files;

import com.google.gson.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
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
}
