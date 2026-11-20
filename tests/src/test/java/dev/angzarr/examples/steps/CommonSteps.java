package dev.angzarr.examples.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.client.Errors;
import io.cucumber.java.en.Then;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared step definitions used across all aggregate tests.
 *
 * <p>Aggregate-specific step classes should set lastRejectedError when their command handling
 * catches an error.
 */
public class CommonSteps {

  // Shared error state - set by aggregate step classes
  private static Errors.CommandRejectedError lastRejectedError;

  /** Set the last rejected error (called by aggregate step classes). */
  public static void setLastRejectedError(Errors.CommandRejectedError error) {
    lastRejectedError = error;
  }

  /** Clear the last rejected error (called at start of scenarios). */
  public static void clearLastRejectedError() {
    lastRejectedError = null;
  }

  /** Get the last rejected error. */
  public static Errors.CommandRejectedError getLastRejectedError() {
    return lastRejectedError;
  }

  @Then("the command fails with status {string}")
  public void commandFailsWithStatus(String status) {
    assertThat(lastRejectedError)
        .withFailMessage("Expected command to fail but it succeeded")
        .isNotNull();
    Status.Code expectedCode = Status.Code.valueOf(status);
    assertThat(lastRejectedError.getStatusCode()).isEqualTo(expectedCode);
  }

  /** Convert bytes to hex string (Java 8 compatible). */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  // Shared cross-step-class state for PM/saga/projector scenarios
  private static Map<String, String> sharedHandStartedData = new HashMap<>();
  private static List<Map<String, String>> sharedActivePlayersData = new ArrayList<>();

  public static void setSharedHandStartedData(Map<String, String> data) {
    sharedHandStartedData = new HashMap<>(data);
  }

  public static Map<String, String> getSharedHandStartedData() {
    return sharedHandStartedData;
  }

  public static void setSharedActivePlayersData(List<Map<String, String>> data) {
    sharedActivePlayersData = new ArrayList<>(data);
  }

  public static List<Map<String, String>> getSharedActivePlayersData() {
    return sharedActivePlayersData;
  }

  @Then("the error message contains {string}")
  public void errorMessageContains(String substring) {
    assertThat(lastRejectedError)
        .withFailMessage("Expected command to fail but it succeeded")
        .isNotNull();
    assertThat(lastRejectedError.getMessage().toLowerCase()).contains(substring.toLowerCase());
  }
}
