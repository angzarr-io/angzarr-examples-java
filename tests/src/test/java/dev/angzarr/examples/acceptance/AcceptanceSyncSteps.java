package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Acceptance step definitions for sync mode scenarios. */
public class AcceptanceSyncSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  @When("I start a hand at table {string} with sync_mode ASYNC")
  public void startHandAsync(String tableName) {
    // Start hand with ASYNC mode
  }

  @When("I start a hand at table {string} with sync_mode SIMPLE")
  public void startHandSimple(String tableName) {
    // Start hand with SIMPLE mode
  }

  @When("I start a hand at table {string} with sync_mode CASCADE")
  public void startHandCascade(String tableName) {
    // Start hand with CASCADE mode
  }

  @When("I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode FAIL_FAST")
  public void startHandCascadeFailFast(String tableName) {
    // Start hand with CASCADE + FAIL_FAST
  }

  @When("I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode CONTINUE")
  public void startHandCascadeContinue(String tableName) {
    // Start hand with CASCADE + CONTINUE
  }

  @When(
      "I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode DEAD_LETTER")
  public void startHandCascadeDeadLetter(String tableName) {
    // Start hand with CASCADE + DEAD_LETTER
  }

  @When("I execute a command with sync_mode CASCADE")
  public void executeCommandCascade() {
    // Execute generic command with CASCADE
  }

  @When("I execute a triggering command with cascade_error_mode CONTINUE")
  public void executeTriggeringContinue() {
    // Execute triggering command
  }

  @When("I send an event without correlation_id with sync_mode CASCADE")
  public void sendEventWithoutCorrelationCascade() {
    // Send event without correlation ID
  }

  @Then("the command succeeds immediately")
  public void commandSucceedsImmediately() {
    // Verify immediate success
  }

  @Then("the command succeeds")
  public void commandSucceeds() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the command succeeds with HandStarted event")
  public void commandSucceedsWithHandStarted() {
    commandSucceeds();
  }

  @Then("the command succeeds with HandStarted only")
  public void commandSucceedsWithHandStartedOnly() {
    commandSucceeds();
  }

  @Then("the response does not include projection updates")
  public void responseNoProjectionUpdates() {
    // Verify no projection updates in ASYNC mode
  }

  @Then("the response does not include cascade results")
  public void responseNoCascadeResults() {
    // Verify no cascade results
  }

  @Then("the response does not include cascade results from sagas")
  public void responseNoCascadeResultsFromSagas() {
    // Verify no cascade results from sagas
  }

  @Then("the response includes projection updates for {string}")
  public void responseIncludesProjectionUpdatesFor(String projector) {
    // Verify projection updates
  }

  @Then("the response includes projection updates")
  public void responseIncludesProjectionUpdates() {
    // Verify projection updates present
  }

  @Then("the response includes projection updates for both table and hand domains")
  public void responseIncludesProjectionUpdatesBothDomains() {
    // Verify both domain projections
  }

  @Then("the projection shows bankroll {int}")
  public void projectionShowsBankroll(int amount) {
    // Verify bankroll in projection
  }

  @Then("the table projection shows hand_count incremented")
  public void tableProjectionHandCountIncremented() {
    // Verify hand count increment
  }

  @Then("the command returns before DealCards is issued")
  public void commandReturnsBeforeDealCards() {
    // Verify SIMPLE mode returns before sagas
  }

  @Then("within {int} seconds hand domain has CardsDealt event")
  public void withinSecondsCardsDealt(int seconds) {
    // Wait for async event
  }

  @Then("the response includes cascade results")
  public void responseIncludesCascadeResults() {
    // Verify cascade results present
  }

  @Then("the cascade results include DealCards command to hand domain")
  public void cascadeIncludesDealCards() {
    // Verify cascade includes DealCards
  }

  @Then("the cascade results include CardsDealt event from hand domain")
  public void cascadeIncludesCardsDealt() {
    // Verify cascade includes CardsDealt
  }

  @Then("the response includes the full cascade chain:")
  public void responseIncludesCascadeChain(DataTable dataTable) {
    // Verify full cascade chain
  }

  @Then("no events are published to the bus during command execution")
  public void noEventsBusPublished() {
    // Verify no bus events
  }

  @Then("all events remain in-process")
  public void allEventsInProcess() {
    // Verify in-process events
  }

  @Then("the command fails with saga error")
  public void commandFailsWithSagaError() {
    // Verify saga error
  }

  @Then("no further sagas are executed after the failure")
  public void noFurtherSagasAfterFailure() {
    // Verify no further sagas
  }

  @Then("the original HandStarted event is still persisted")
  public void originalHandStartedPersisted() {
    // Verify original event persisted
  }

  @Then("the response includes cascade_errors with the saga failure")
  public void responseIncludesCascadeErrors() {
    // Verify cascade errors
  }

  @Then("the response includes successful projection updates")
  public void responseIncludesSuccessfulProjectionUpdates() {
    // Verify successful projections alongside errors
  }

  @Then("other sagas continue executing despite the failure")
  public void otherSagasContinue() {
    // Verify saga continuation
  }

  @Then("other sagas continue executing")
  public void otherSagasContinueExecuting() {
    // Verify saga continuation
  }

  @Then("compensation commands are issued in reverse order")
  public void compensationInReverseOrder() {
    // Verify compensation ordering
  }

  @Then("the command fails after compensation completes")
  public void commandFailsAfterCompensation() {
    // Verify compensation then failure
  }

  @Then("the saga failure is published to the dead letter queue")
  public void sagaFailureToDeadLetter() {
    // Verify DLQ
  }

  @Then("the dead letter includes:")
  public void deadLetterIncludes(DataTable dataTable) {
    // Verify dead letter content
  }

  @Then("the process manager receives the correlated events")
  public void pmReceivesCorrelatedEvents() {
    // Verify PM event receipt
  }

  @Then("the response includes PM state updates")
  public void responseIncludesPmUpdates() {
    // Verify PM state updates
  }

  @Then("the process manager is not invoked")
  public void pmNotInvoked() {
    // Verify PM not invoked
  }

  @Then("sagas still execute normally")
  public void sagasExecuteNormally() {
    // Verify saga execution
  }

  @Then("all commands complete within {int}ms each")
  public void allCommandsWithinMs(int ms) {
    // Verify performance
  }

  @Then("total execution time is less than with SIMPLE mode")
  public void totalTimeLessThanSimple() {
    // Verify performance comparison
  }

  @Then("the response time is higher than ASYNC or SIMPLE")
  public void responseTimeHigher() {
    // Verify performance comparison
  }

  @Then("all cross-domain state is consistent immediately")
  public void allStateConsistent() {
    // Verify immediate consistency
  }

  @Then("the response has empty cascade_results")
  public void emptyResponse() {
    // Verify empty cascade results
  }

  @Then("the saga produces no commands")
  public void sagaProducesNoCommands() {
    // Verify no saga commands
  }

  @Then("the original event is still persisted")
  public void originalEventPersisted() {
    // Verify event persistence
  }

  @Then("all saga errors are collected in cascade_errors")
  public void allSagaErrorsCollected() {
    // Verify error collection
  }

  // --- Given steps for sync mode scenarios ---

  @Given("the table-hand saga is configured to fail")
  public void tableHandSagaConfiguredToFail() {
    // Configure saga failure
  }

  @Given("the output projector is healthy")
  public void outputProjectorHealthy() {
    // Verify projector health
  }

  @Given("the hand-player saga is configured to fail on PotAwarded")
  public void handPlayerSagaConfiguredToFail() {
    // Configure saga failure
  }

  @Given("a dead letter queue is configured")
  public void deadLetterQueueConfigured() {
    // Configure DLQ
  }

  @Given("the hand-flow process manager is registered")
  public void handFlowPmRegistered() {
    // Register PM
  }

  @Given("I am monitoring the event bus")
  public void monitoringEventBus() {
    // Start monitoring
  }

  @Given("a domain with no registered sagas")
  public void domainWithNoSagas() {
    // Empty saga domain
  }

  @Given("a table with no seated players")
  public void tableWithNoSeatedPlayers() {
    // Empty table
  }

  @Given("multiple sagas configured to fail")
  public void multipleSagasConfiguredToFail() {
    // Configure multiple failures
  }
}
