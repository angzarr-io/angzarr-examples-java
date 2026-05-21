package dev.angzarr.examples.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Message;
import dev.angzarr.EventPage;
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

  @Then("the error message equals {string}")
  public void errorMessageEquals(String expected) {
    assertThat(lastRejectedError)
        .withFailMessage("Expected command to fail but it succeeded")
        .isNotNull();
    assertThat(lastRejectedError.getMessage()).isEqualTo(expected);
  }

  // -------- Shared rebuilt TournamentState (for PM rebuild scenarios) --------
  private static Object sharedTournamentStateHelper;

  public static void setSharedTournamentStateHelper(Object helper) {
    sharedTournamentStateHelper = helper;
  }

  public static Object getSharedTournamentStateHelper() {
    return sharedTournamentStateHelper;
  }

  public static void clearSharedTournamentStateHelper() {
    sharedTournamentStateHelper = null;
  }

  // -------- Shared Player event book (cross-aggregate preflight) --------
  // PlayerSteps publishes its accumulated EventPages here so ReservationSteps
  // can preflight against bankroll / existence before invoking handlers.
  private static final List<EventPage> sharedPlayerEvents = new ArrayList<>();

  public static void resetSharedPlayerEvents() {
    sharedPlayerEvents.clear();
  }

  public static void publishPlayerEvent(EventPage page) {
    sharedPlayerEvents.add(page);
  }

  public static List<EventPage> getSharedPlayerEvents() {
    return new ArrayList<>(sharedPlayerEvents);
  }

  // -------- Shared last-emitted-event registry --------
  private static Message lastResultEvent;

  public static void setLastResultEvent(Message event) {
    lastResultEvent = event;
  }

  public static Message getLastResultEvent() {
    return lastResultEvent;
  }

  static void assertResultIsEventNamed(String expectedSimpleName) {
    assertThat(lastResultEvent)
        .withFailMessage("Expected a result event but got null (likely rejected)")
        .isNotNull();
    String actualFqn = lastResultEvent.getDescriptorForType().getFullName();
    String actualSimple = actualFqn.substring(actualFqn.lastIndexOf('.') + 1);
    assertThat(actualSimple).isEqualTo(expectedSimpleName);
  }

  // -------- "a X event is emitted" FQN bindings (clears 14 undefineds) --------

  @Then("a angzarr_client.proto.examples.ActionTaken event is emitted")
  public void aActionTakenEmitted() {
    assertResultIsEventNamed("ActionTaken");
  }

  @Then("a angzarr_client.proto.examples.CommunityCardsDealt event is emitted")
  public void aCommunityCardsDealtEmitted() {
    assertResultIsEventNamed("CommunityCardsDealt");
  }

  @Then("a angzarr_client.proto.examples.StudCommunityCardDealt event is emitted")
  public void aStudCommunityCardDealtEmitted() {
    assertResultIsEventNamed("StudCommunityCardDealt");
  }

  @Then("a angzarr_client.proto.examples.StudStreetDealt event is emitted")
  public void aStudStreetDealtEmitted() {
    assertResultIsEventNamed("StudStreetDealt");
  }

  @Then("a angzarr_client.proto.examples.MisdealDeclared event is emitted")
  public void aMisdealDeclaredEmitted() {
    assertResultIsEventNamed("MisdealDeclared");
  }

  @Then("a angzarr_client.proto.examples.PotAwarded event is emitted")
  public void aPotAwardedEmitted() {
    assertResultIsEventNamed("PotAwarded");
  }

  @Then("a angzarr_client.proto.examples.BringInCorrected event is emitted")
  public void aBringInCorrectedEmitted() {
    assertResultIsEventNamed("BringInCorrected");
  }

  @Then("a angzarr_client.proto.examples.ButtonCardReplaced event is emitted")
  public void aButtonCardReplacedEmitted() {
    assertResultIsEventNamed("ButtonCardReplaced");
  }

  @Then("a angzarr_client.proto.examples.PrematureFlopDetected event is emitted")
  public void aPrematureFlopDetectedEmitted() {
    assertResultIsEventNamed("PrematureFlopDetected");
  }

  @Then("a angzarr_client.proto.examples.PrematureRiverDetected event is emitted")
  public void aPrematureRiverDetectedEmitted() {
    assertResultIsEventNamed("PrematureRiverDetected");
  }

  @Then("a angzarr_client.proto.examples.PrematureTurnDetected event is emitted")
  public void aPrematureTurnDetectedEmitted() {
    assertResultIsEventNamed("PrematureTurnDetected");
  }

  @Then("a angzarr_client.proto.examples.PrematureStudCardDetected event is emitted")
  public void aPrematureStudCardDetectedEmitted() {
    assertResultIsEventNamed("PrematureStudCardDetected");
  }

  @Then("a angzarr_client.proto.examples.SeventhStreetCardReplaced event is emitted")
  public void aSeventhStreetCardReplacedEmitted() {
    assertResultIsEventNamed("SeventhStreetCardReplaced");
  }

  @Then("a angzarr_client.proto.examples.StudDoorCardSelected event is emitted")
  public void aStudDoorCardSelectedEmitted() {
    assertResultIsEventNamed("StudDoorCardSelected");
  }

  @Then("a angzarr_client.proto.examples.StudDownCardConverted event is emitted")
  public void aStudDownCardConvertedEmitted() {
    assertResultIsEventNamed("StudDownCardConverted");
  }

  @Then("a angzarr_client.proto.examples.UnderbetCorrected event is emitted")
  public void aUnderbetCorrectedEmitted() {
    assertResultIsEventNamed("UnderbetCorrected");
  }
}
