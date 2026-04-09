package dev.angzarr.examples;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Cucumber test runner for acceptance feature files.
 *
 * <p>Runs against features in angzarr-project/features/acceptance/ using the acceptance step
 * definitions. When PLAYER_URL is set, commands are sent via gRPC to a running coordinator;
 * otherwise, commands are dispatched in-process.
 *
 * <p>Activated by the {@code cucumber-acceptance} tag in Gradle.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/acceptance")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.angzarr.examples.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class CucumberAcceptanceTest {}
