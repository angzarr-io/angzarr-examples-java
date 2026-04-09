package dev.angzarr.examples.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** Cucumber Spring configuration for acceptance test execution. */
@CucumberContextConfiguration
@SpringBootTest(classes = CucumberAcceptanceConfiguration.class)
public class CucumberAcceptanceConfiguration {}
