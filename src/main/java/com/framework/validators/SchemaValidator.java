package com.framework.validators;

import com.framework.constants.FrameworkConstants;
import io.qameta.allure.Step;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;

import java.io.File;

/**
 * Validates a response body against a JSON Schema file stored under
 * src/test/resources/schemas/. Schema-based contract checks catch field
 * type/shape regressions that field-by-field assertions would miss.
 */
public final class SchemaValidator {

    private SchemaValidator() {
    }

    @Step("Validate response against schema {schemaFileName}")
    public static void validate(Response response, String schemaFileName) {
        File schemaFile = new File(FrameworkConstants.SCHEMA_DIR + schemaFileName);
        response.then().assertThat().body(
                JsonSchemaValidator.matchesJsonSchema(schemaFile));
    }

    @Step("Validate response against schema {schemaFileName} (strict)")
    public static void validateStrict(Response response, String schemaFileName) {
        File schemaFile = new File(FrameworkConstants.SCHEMA_DIR + schemaFileName);
        response.then().assertThat().body(
                JsonSchemaValidator.matchesJsonSchema(schemaFile)
                        .using(io.restassured.module.jsv.JsonSchemaValidatorSettings
                                .settings()
                                .with()
                                .checkedValidation(true)));
    }
}
