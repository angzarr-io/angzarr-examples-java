package dev.angzarr.examples.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Minimal Spring context for Cucumber acceptance tests.
 *
 * <p>Does NOT start a full Spring Boot application. Only provides the Spring context required by
 * cucumber-spring's auto-detection. No beans are loaded; step classes use manual construction.
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = CucumberAcceptanceConfiguration.class)
public class CucumberAcceptanceConfiguration {}
