package com.framework.dataproviders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.framework.utils.FileReaderUtils;
import com.framework.utils.JsonUtils;

import java.util.List;
import java.util.Map;

/**
 * Generic, application-agnostic @DataProvider file-reading helper. Each
 * application defines its own concrete {@code @DataProvider} methods
 * (e.g. {@code com.framework.apps.appA.dataproviders.AppADataProviders}),
 * delegating to {@link #fromJsonArray} here so the file-reading logic
 * itself is written once, not per application.
 */
public final class DataProviders {

    private DataProviders() {
    }

    /** Generic JSON-array-of-objects reader; each application's data provider points this at its own file. */
    public static Object[][] fromJsonArray(String filePath) {
        String content = FileReaderUtils.readAsString(filePath);
        List<Map<String, Object>> rows = JsonUtils.fromJsonList(content,
            new TypeReference<List<Map<String, Object>>>() {});
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }
}
