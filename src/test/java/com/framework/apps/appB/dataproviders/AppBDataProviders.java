package com.framework.apps.appB.dataproviders;

import com.framework.dataproviders.DataProviders;
import org.testng.annotations.DataProvider;

/**
 * App B (Restful Booker) @DataProvider sources, delegating file-reading
 * to the generic, application-agnostic {@link DataProviders#fromJsonArray}.
 */
public final class AppBDataProviders {

    private AppBDataProviders() {
    }

    @DataProvider(name = "createBookingData")
    public static Object[][] createBookingData() {
        return DataProviders.fromJsonArray("src/test/resources/testdata/appB/create_booking_data.json");
    }
}
