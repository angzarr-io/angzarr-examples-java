package dev.angzarr.examples.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;

/** Minimal Spring context for acceptance tests. No component scanning, no auto-configuration. */
@CucumberContextConfiguration
@Configuration
public class CucumberAcceptanceConfiguration {}
