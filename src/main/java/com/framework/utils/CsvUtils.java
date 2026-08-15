package com.framework.utils;

import com.framework.exceptions.FrameworkException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * Reads CSV test-data files into raw rows. Row 0 is treated as the header
 * by convention; callers decide how to map columns to fields.
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    public static List<String[]> readAllRows(String filePath) {
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            return reader.readAll();
        } catch (IOException | CsvException e) {
            throw new FrameworkException("Failed to read CSV file: " + filePath, e);
        }
    }

    /** Returns data rows only (header row excluded) as Object[][] for a TestNG @DataProvider. */
    public static Object[][] toDataProvider(String filePath) {
        List<String[]> rows = readAllRows(filePath);
        if (rows.isEmpty()) {
            return new Object[0][0];
        }
        Object[][] data = new Object[rows.size() - 1][];
        for (int i = 1; i < rows.size(); i++) {
            data[i - 1] = rows.get(i);
        }
        return data;
    }
}
