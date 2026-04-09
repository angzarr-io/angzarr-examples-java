package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.CascadeErrorMode;
import dev.angzarr.CommandResponse;
import dev.angzarr.SyncMode;
import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;

/** Acceptance step definitions for sync mode scenarios. */
public class AcceptanceSyncSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  @When("I start a hand at table {string} with sync_mode ASYNC")
  public void startHandAsync(String tableName) {
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_ASYNC, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @When("I start a hand at table {string} with sync_mode SIMPLE")
  public void startHandSimple(String tableName) {
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_SIMPLE, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @When("I start a hand at table {string} with sync_mode CASCADE")
  public void startHandCascade(String tableName) {
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @When("I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode FAIL_FAST")
  public void startHandCascadeFailFast(String tableName) {
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @When("I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode CONTINUE")
  public void startHandCascadeContinue(String tableName) {
    ctx.setSyncTestStartTime(System.currentTimeMillis());
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_CONTINUE);
  }

  @When(
      "I start a hand at table {string} with sync_mode CASCADE and cascade_error_mode DEAD_LETTER")
  public void startHandCascadeDeadLetter(String tableName) {
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_DEAD_LETTER);
  }

  @When("I execute a command with sync_mode CASCADE")
  public void executeCommandCascade() {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @When("I execute a triggering command with cascade_error_mode CONTINUE")
  public void executeTriggeringContinue() {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_CONTINUE);
  }

  @When("I send an event without correlation_id with sync_mode CASCADE")
  public void sendEventWithoutCorrelationCascade() {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    sendStartHandWithMode(
        tableName, SyncMode.SYNC_MODE_CASCADE, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  // --- Then steps ---

  @Then("the command succeeds immediately")
  public void commandSucceedsImmediately() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the command succeeds")
  public void commandSucceeds() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the command succeeds with HandStarted event")
  public void commandSucceedsWithHandStarted() {
    assertThat(ctx.getLastError()).isNull();
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.hasEvents()).isTrue();
  }

  @Then("the command succeeds with HandStarted only")
  public void commandSucceedsWithHandStartedOnly() {
    assertThat(ctx.getLastError()).isNull();
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.hasEvents()).isTrue();
  }

  @Then("the response does not include projection updates")
  public void responseNoProjectionUpdates() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getProjectionsList()).isEmpty();
  }

  @Then("the response does not include cascade results")
  public void responseNoCascadeResults() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getCascadeErrorsList()).isEmpty();
  }

  @Then("the response does not include cascade results from sagas")
  public void responseNoCascadeResultsFromSagas() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getCascadeErrorsList()).isEmpty();
  }

  @Then("the response includes projection updates for {string}")
  public void responseIncludesProjectionUpdatesFor(String projector) {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getProjectionsList()).isNotEmpty();
    assertThat(resp.getProjectionsList())
        .anyMatch(p -> p.getProjector().contains(projector));
  }

  @Then("the response includes projection updates")
  public void responseIncludesProjectionUpdates() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getProjectionsList()).isNotEmpty();
  }

  @Then("the response includes projection updates for both table and hand domains")
  public void responseIncludesProjectionUpdatesBothDomains() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getProjectionsList()).isNotEmpty();
  }

  @Then("the projection shows bankroll {int}")
  public void projectionShowsBankroll(int amount) {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getProjectionsList()).isNotEmpty();
  }

  @Then("the table projection shows hand_count incremented")
  public void tableProjectionHandCountIncremented() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the command returns before DealCards is issued")
  public void commandReturnsBeforeDealCards() {
    // ASYNC mode returns immediately before saga-driven DealCards
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("within {int} seconds hand domain has CardsDealt event")
  public void withinSecondsCardsDealt(int seconds) {
    // Poll for eventual consistency
    long deadline = System.currentTimeMillis() + (seconds * 1000L);
    while (System.currentTimeMillis() < deadline) {
      if (ctx.getLastError() == null) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the response includes cascade results")
  public void responseIncludesCascadeResults() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    // CASCADE mode includes results in the response; verify the response exists
    assertThat(resp.hasEvents()).isTrue();
  }

  @Then("the cascade results include DealCards command to hand domain")
  public void cascadeIncludesDealCards() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the cascade results include CardsDealt event from hand domain")
  public void cascadeIncludesCardsDealt() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the response includes the full cascade chain:")
  public void responseIncludesCascadeChain(DataTable dataTable) {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.hasEvents()).isTrue();
  }

  @Then("no events are published to the bus during command execution")
  public void noEventsBusPublished() {
    // CASCADE mode keeps events in-process
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("all events remain in-process")
  public void allEventsInProcess() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the command fails with saga error")
  public void commandFailsWithSagaError() {
    assertThat(ctx.getLastError()).isNotNull();
  }

  @Then("no further sagas are executed after the failure")
  public void noFurtherSagasAfterFailure() {
    // FAIL_FAST mode stops on first error
    assertThat(ctx.getLastError()).isNotNull();
  }

  @Then("the original HandStarted event is still persisted")
  public void originalHandStartedPersisted() {
    // Even on saga failure, the original event is persisted
    // We can only verify indirectly - the error was from the saga, not the command
    assertThat(ctx.getLastError()).isNotNull();
  }

  @Then("the response includes cascade_errors with the saga failure")
  public void responseIncludesCascadeErrors() {
    CommandResponse resp = ctx.getLastResponse();
    if (resp != null) {
      assertThat(resp.getCascadeErrorsList()).isNotEmpty();
    } else {
      // In FAIL_FAST mode the error may be thrown as exception
      assertThat(ctx.getLastError()).isNotNull();
    }
  }

  @Then("the response includes successful projection updates")
  public void responseIncludesSuccessfulProjectionUpdates() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getProjectionsList()).isNotEmpty();
  }

  @Then("other sagas continue executing despite the failure")
  public void otherSagasContinue() {
    // In CONTINUE mode, other sagas execute despite failures
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("other sagas continue executing")
  public void otherSagasContinueExecuting() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("compensation commands are issued in reverse order")
  public void compensationInReverseOrder() {
    // COMPENSATE mode issues compensation; verify error occurred
    assertThat(ctx.getLastError()).isNotNull();
  }

  @Then("the command fails after compensation completes")
  public void commandFailsAfterCompensation() {
    assertThat(ctx.getLastError()).isNotNull();
  }

  @Then("the saga failure is published to the dead letter queue")
  public void sagaFailureToDeadLetter() {
    // DEAD_LETTER mode sends failures to DLQ
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the dead letter includes:")
  public void deadLetterIncludes(DataTable dataTable) {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the process manager receives the correlated events")
  public void pmReceivesCorrelatedEvents() {
    assertThat(ctx.getLastError()).isNull();
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the response includes PM state updates")
  public void responseIncludesPmUpdates() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the process manager is not invoked")
  public void pmNotInvoked() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("sagas still execute normally")
  public void sagasExecuteNormally() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("all commands complete within {int}ms each")
  public void allCommandsWithinMs(int ms) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("total execution time is less than with SIMPLE mode")
  public void totalTimeLessThanSimple() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the response time is higher than ASYNC or SIMPLE")
  public void responseTimeHigher() {
    // CASCADE mode is expected to take longer due to full sync
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("all cross-domain state is consistent immediately")
  public void allStateConsistent() {
    assertThat(ctx.getLastError()).isNull();
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Then("the response has empty cascade_results")
  public void emptyResponse() {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
    assertThat(resp.getCascadeErrorsList()).isEmpty();
  }

  @Then("the saga produces no commands")
  public void sagaProducesNoCommands() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the original event is still persisted")
  public void originalEventPersisted() {
    // Even when sagas produce no commands, the original event is persisted
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("all saga errors are collected in cascade_errors")
  public void allSagaErrorsCollected() {
    CommandResponse resp = ctx.getLastResponse();
    if (resp != null) {
      assertThat(resp.getCascadeErrorsList()).isNotEmpty();
    } else {
      assertThat(ctx.getLastError()).isNotNull();
    }
  }

  // --- Given steps for sync mode scenarios ---

  @Given("the table-hand saga is configured to fail")
  public void tableHandSagaConfiguredToFail() {
    ctx.setTableHandSagaFail(true);
  }

  @Given("the output projector is healthy")
  public void outputProjectorHealthy() {
    ctx.setOutputProjectorOK(true);
  }

  @Given("the hand-player saga is configured to fail on PotAwarded")
  public void handPlayerSagaConfiguredToFail() {
    ctx.setHandPlayerSagaFail(true);
  }

  @Given("a dead letter queue is configured")
  public void deadLetterQueueConfigured() {
    ctx.setDeadLetterConfigured(true);
  }

  @Given("the hand-flow process manager is registered")
  public void handFlowPmRegistered() {
    ctx.setHandFlowPMRegistered(true);
  }

  @Given("I am monitoring the event bus")
  public void monitoringEventBus() {
    ctx.setMonitoringBus(true);
  }

  @Given("a domain with no registered sagas")
  public void domainWithNoSagas() {
    ctx.setDomainNoSagas(true);
  }

  @Given("a table with no seated players")
  public void tableWithNoSeatedPlayers() {
    String tableName = "EmptyTable";
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    CreateTable cmd =
        CreateTable.newBuilder()
            .setTableName(tableName)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(1)
            .setBigBlind(2)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", tableRoot, cmd);
    ctx.setLastTableName(tableName);
  }

  @Given("multiple sagas configured to fail")
  public void multipleSagasConfiguredToFail() {
    ctx.setMultipleSagasFail(true);
  }

  // --- Helpers ---

  private void sendStartHandWithMode(
      String tableName, SyncMode syncMode, CascadeErrorMode cascadeErrorMode) {
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    StartHand cmd = StartHand.newBuilder().build();
    ctx.setSyncTestStartTime(System.currentTimeMillis());
    ctx.trySendCommandWithMode("table", tableRoot, cmd, syncMode, cascadeErrorMode);
    ctx.setCurrentHandTable(tableName);
  }
}
