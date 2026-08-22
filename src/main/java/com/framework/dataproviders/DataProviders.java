package com.framework.dataproviders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.framework.utils.CsvUtils;
import com.framework.utils.FileReaderUtils;
import com.framework.utils.JsonUtils;
import org.testng.annotations.DataProvider;

import java.util.List;
import java.util.Map;

/**
 * Reusable @DataProvider sources. Test classes reference these by name
 * (e.g. {@code @Test(dataProvider = "jsonUsers", dataProviderClass = DataProviders.class)})
 * instead of each hand-rolling file-reading boilerplate.
 */
public final class DataProviders {

    private DataProviders() {
    }

    /** Generic JSON-array-of-objects reader; point PROVIDER_FILE per usage via system property, or wrap per-file. */
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

    @DataProvider(name = "createUserData")
    public static Object[][] createUserData() {
        return fromJsonArray("src/test/resources/testdata/create_user_data.json");
    }

    @DataProvider(name = "invalidLoginData")
    public static Object[][] invalidLoginData() {
        return fromJsonArray("src/test/resources/testdata/invalid_login_data.json");
    }

    @DataProvider(name = "userIdsCsv")
    public static Object[][] userIdsCsv() {
        return CsvUtils.toDataProvider("src/test/resources/testdata/user_ids.csv");
    }
}
