package com.framework.apps.appA.dataproviders;

import com.framework.dataproviders.DataProviders;
import com.framework.utils.CsvUtils;
import org.testng.annotations.DataProvider;

/**
 * App A (Users) @DataProvider sources. Test classes reference these by
 * name (e.g. {@code @Test(dataProvider = "createUserData", dataProviderClass = AppADataProviders.class)}),
 * delegating file-reading to the generic, application-agnostic
 * {@link DataProviders#fromJsonArray}/{@link CsvUtils#toDataProvider}.
 */
public final class AppADataProviders {

    private AppADataProviders() {
    }

    @DataProvider(name = "createUserData")
    public static Object[][] createUserData() {
        return DataProviders.fromJsonArray("src/test/resources/testdata/appA/create_user_data.json");
    }

    @DataProvider(name = "invalidLoginData")
    public static Object[][] invalidLoginData() {
        return DataProviders.fromJsonArray("src/test/resources/testdata/appA/invalid_login_data.json");
    }

    @DataProvider(name = "userIdsCsv")
    public static Object[][] userIdsCsv() {
        return CsvUtils.toDataProvider("src/test/resources/testdata/appA/user_ids.csv");
    }
}
