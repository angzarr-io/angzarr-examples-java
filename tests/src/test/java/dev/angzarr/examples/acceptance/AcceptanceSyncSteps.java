package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Acceptance step definitions for sync mode scenarios. */
public class AcceptanceSyncSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  @When("I start a hand at table {string} with sync_mode ASYNC")
  public void startHandAsync(String tableName) {
    throw new PendingException();
  }

  @When("I start a hand at table {string} with sync_mode SIMPLE")
  public void startHandSimple(String tableName) {
    throw new PendingException();
  }

  @When("I start a hand at table {string} with sync_mode CASCADE")
  public void startHandCascade(String tableName) {
    throw new PendingException();
  }

  @When("I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode FAIL_FAST")
  public void startHandCascadeFailFast(String tableName) {
    throw new PendingException();
  }

  @When("I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode CONTINUE")
  public void startHandCascadeContinue(String tableName) {
    throw new PendingException();
  }

  @When(
      "I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode DEAD_LETTER")
  public void startHandCascadeDeadLetter(String tableName) {
    throw new PendingException();
  }

  @When("I execute a command with sync_mode CASCADE")
  public void executeCommandCascade() {
    throw new PendingException();
  }

  @When("I execute a triggering command with cascade_error_mode CONTINUE")
  public void executeTriggeringContinue() {
    throw new PendingException();
  }

  @When("I send an event without correlation_id with sync_mode CASCADE")
  public void sendEventWithoutCorrelationCascade() {
    throw new PendingException();
  }

  @Then("the command succeeds immediately")
  public void commandSucceedsImmediately() {
    throw new PendingException();
  }

  @Then("the command succeeds")
  public void commandSucceeds() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the command succeeds with HandStarted event")
  public void commandSucceedsWithHandStarted() {
    throw new PendingException();
  }

  @Then("the command succeeds with HandStarted only")
  public void commandSucceedsWithHandStartedOnly() {
    throw new PendingException();
  }

  @Then("the response does not include projection updates")
  public void responseNoProjectionUpdates() {
    throw new PendingException();
  }

  @Then("the response does not include cascade results")
  public void responseNoCascadeResults() {
    throw new PendingException();
  }

  @Then("the response does not include cascade results from sagas")
  public void responseNoCascadeResultsFromSagas() {
    throw new PendingException();
  }

  @Then("the response includes projection updates for {string}")
  public void responseIncludesProjectionUpdatesFor(String projector) {
    throw new PendingException();
  }

  @Then("the response includes projection updates")
  public void responseIncludesProjectionUpdates() {
    throw new PendingException();
  }

  @Then("the response includes projection updates for both table and hand domains")
  public void responseIncludesProjectionUpdatesBothDomains() {
    throw new PendingException();
  }

  @Then("the projection shows bankroll {int}")
  public void projectionShowsBankroll(int amount) {
    throw new PendingException();
  }

  @Then("the table projection shows hand_count incremented")
  public void tableProjectionHandCountIncremented() {
    throw new PendingException();
  }

  @Then("the command returns before DealCards is issued")
  public void commandReturnsBeforeDealCards() {
    throw new PendingException();
  }

  @Then("within {int} seconds hand domain has CardsDealt event")
  public void withinSecondsCardsDealt(int seconds) {
    throw new PendingException();
  }

  @Then("the response includes cascade results")
  public void responseIncludesCascadeResults() {
    throw new PendingException();
  }

  @Then("the cascade results include DealCards command to hand domain")
  public void cascadeIncludesDealCards() {
    throw new PendingException();
  }

  @Then("the cascade results include CardsDealt event from hand domain")
  public void cascadeIncludesCardsDealt() {
    throw new PendingException();
  }

  @Then("the response includes the full cascade chain:")
  public void responseIncludesCascadeChain(DataTable dataTable) {
    throw new PendingException();
  }

  @Then("no events are published to the bus during command execution")
  public void noEventsBusPublished() {
    throw new PendingException();
  }

  @Then("all events remain in-process")
  public void allEventsInProcess() {
    throw new PendingException();
  }

  @Then("the command fails with saga error")
  public void commandFailsWithSagaError() {
    throw new PendingException();
  }

  @Then("no further sagas are executed after the failure")
  public void noFurtherSagasAfterFailure() {
    throw new PendingException();
  }

  @Then("the original HandStarted event is still persisted")
  public void originalHandStartedPersisted() {
    throw new PendingException();
  }

  @Then("the response includes cascade_errors with the saga failure")
  public void responseIncludesCascadeErrors() {
    throw new PendingException();
  }

  @Then("the response includes successful projection updates")
  public void responseIncludesSuccessfulProjectionUpdates() {
    throw new PendingException();
  }

  @Then("other sagas continue executing despite the failure")
  public void otherSagasContinue() {
    throw new PendingException();
  }

  @Then("other sagas continue executing")
  public void otherSagasContinueExecuting() {
    throw new PendingException();
  }

  @Then("compensation commands are issued in reverse order")
  public void compensationInReverseOrder() {
    throw new PendingException();
  }

  @Then("the command fails after compensation completes")
  public void commandFailsAfterCompensation() {
    throw new PendingException();
  }

  @Then("the saga failure is published to the dead letter queue")
  public void sagaFailureToDeadLetter() {
    throw new PendingException();
  }

  @Then("the dead letter includes:")
  public void deadLetterIncludes(DataTable dataTable) {
    throw new PendingException();
  }

  @Then("the process manager receives the correlated events")
  public void pmReceivesCorrelatedEvents() {
    throw new PendingException();
  }

  @Then("the response includes PM state updates")
  public void responseIncludesPmUpdates() {
    throw new PendingException();
  }

  @Then("the process manager is not invoked")
  public void pmNotInvoked() {
    throw new PendingException();
  }

  @Then("sagas still execute normally")
  public void sagasExecuteNormally() {
    throw new PendingException();
  }

  @Then("all commands complete within {int}ms each")
  public void allCommandsWithinMs(int ms) {
    throw new PendingException();
  }

  @Then("total execution time is less than with SIMPLE mode")
  public void totalTimeLessThanSimple() {
    throw new PendingException();
  }

  @Then("the response time is higher than ASYNC or SIMPLE")
  public void responseTimeHigher() {
    throw new PendingException();
  }

  @Then("all cross-domain state is consistent immediately")
  public void allStateConsistent() {
    throw new PendingException();
  }

  @Then("the response has empty cascade_results")
  public void emptyResponse() {
    throw new PendingException();
  }

  @Then("the saga produces no commands")
  public void sagaProducesNoCommands() {
    throw new PendingException();
  }

  @Then("the original event is still persisted")
  public void originalEventPersisted() {
    throw new PendingException();
  }

  @Then("all saga errors are collected in cascade_errors")
  public void allSagaErrorsCollected() {
    throw new PendingException();
  }

  // --- Given steps for sync mode scenarios ---

  @Given("the table-hand saga is configured to fail")
  public void tableHandSagaConfiguredToFail() {
    throw new PendingException();
  }

  @Given("the output projector is healthy")
  public void outputProjectorHealthy() {
    throw new PendingException();
  }

  @Given("the hand-player saga is configured to fail on PotAwarded")
  public void handPlayerSagaConfiguredToFail() {
    throw new PendingException();
  }

  @Given("a dead letter queue is configured")
  public void deadLetterQueueConfigured() {
    throw new PendingException();
  }

  @Given("the hand-flow process manager is registered")
  public void handFlowPmRegistered() {
    throw new PendingException();
  }

  @Given("I am monitoring the event bus")
  public void monitoringEventBus() {
    throw new PendingException();
  }

  @Given("a domain with no registered sagas")
  public void domainWithNoSagas() {
    throw new PendingException();
  }

  @Given("a table with no seated players")
  public void tableWithNoSeatedPlayers() {
    throw new PendingException();
  }

  @Given("multiple sagas configured to fail")
  public void multipleSagasConfiguredToFail() {
    throw new PendingException();
  }
}
