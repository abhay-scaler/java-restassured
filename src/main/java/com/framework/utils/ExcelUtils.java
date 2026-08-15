package com.framework.utils;

import com.framework.exceptions.FrameworkException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads .xlsx test-data sheets into Object[][] for TestNG @DataProvider use.
 * Row 0 is assumed to be a header row and is skipped.
 */
public final class ExcelUtils {

    private ExcelUtils() {
    }

    public static Object[][] readSheet(String filePath, String sheetName) {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new FrameworkException("Sheet '" + sheetName + "' not found in " + filePath);
            }

            DataFormatter formatter = new DataFormatter();
            List<Object[]> rows = new ArrayList<>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                int lastCell = row.getLastCellNum();
                Object[] values = new Object[lastCell];
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    values[c] = formatter.formatCellValue(cell);
                }
                rows.add(values);
            }
            return rows.toArray(new Object[0][]);

        } catch (IOException e) {
            throw new FrameworkException("Failed to read Excel file: " + filePath, e);
        }
    }
}
