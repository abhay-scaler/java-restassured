package com.framework.retry;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Auto-attaches {@link RetryAnalyzer} to every @Test method via TestNG's
 * annotation transformer hook, so individual test authors never need to
 * write {@code retryAnalyzer = RetryAnalyzer.class} on each method.
 */
public class RetryListener implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                           Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
