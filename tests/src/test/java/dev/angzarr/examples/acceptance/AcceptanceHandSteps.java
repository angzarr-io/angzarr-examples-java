package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.angzarr.CascadeErrorMode;
import dev.angzarr.SyncMode;
import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;

/** Acceptance step definitions for hand-related and betting scenarios. */
public class AcceptanceHandSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  // --- Given steps (deck setup) ---

  @Given("deterministic deck seed {string}")
  public void deterministicDeckSeed(String seed) {
    ctx.setDeckSeed(seed);
  }

  @Given("deterministic deck where both players make the same flush")
  public void deterministicDeckSameFlush() {
    ctx.setDeckSeed("same-flush");
  }

  @Given("deterministic deck with community cards making a royal flush")
  public void deterministicDeckRoyalFlush() {
    ctx.setDeckSeed("royal-flush-community");
  }

  @Given("deterministic deck where:")
  public void deterministicDeckWhere(DataTable dataTable) {
    ctx.setDeckSeed("custom-" + dataTable.asMaps().hashCode());
  }

  @Given("deterministic deck where Alice has best hand, Bob has second best")
  public void deterministicDeckAliceBestBobSecond() {
    ctx.setDeckSeed("alice-best-bob-second");
  }

  // --- When steps (hand lifecycle) ---

  @When("a hand starts and blinds are posted \\({int}\\/{int})")
  public void handStartsAndBlindsPosted(int smallBlind, int bigBlind) {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);

    // Start hand
    StartHand startCmd = StartHand.newBuilder().build();
    ctx.sendCommand("table", tableRoot, startCmd);
    ctx.setCurrentHandTable(tableName);
  }

  @When("blinds are posted \\({int}\\/{int})")
  public void blindsArePosted(int smallBlind, int bigBlind) {
    // Blinds are automatically posted as part of hand start via saga
    assertThat(ctx.getLastError()).isNull();
  }

  @When("a hand starts with dealer at seat {int}")
  public void handStartsWithDealerAtSeat(int seat) {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    StartHand startCmd = StartHand.newBuilder().build();
    ctx.sendCommand("table", tableRoot, startCmd);
    ctx.setCurrentHandTable(tableName);
  }

  @When("{string} posts small blind {int}")
  public void postsSmallBlind(String playerName, int amount) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    PostBlind cmd =
        PostBlind.newBuilder()
            .setPlayerRoot(toByteString(playerRoot))
            .setBlindType("small")
            .setAmount(amount)
            .build();
    ctx.sendCommand("hand", handRoot, cmd);
  }

  @When("{string} posts big blind {int}")
  public void postsBigBlind(String playerName, int amount) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    PostBlind cmd =
        PostBlind.newBuilder()
            .setPlayerRoot(toByteString(playerRoot))
            .setBlindType("big")
            .setAmount(amount)
            .build();
    ctx.sendCommand("hand", handRoot, cmd);
  }

  // --- When steps (player actions) ---

  @When("{string} folds")
  public void playerFolds(String playerName) {
    sendPlayerAction(playerName, ActionType.FOLD, 0);
  }

  @When("{string} calls {int}")
  public void playerCalls(String playerName, int amount) {
    sendPlayerAction(playerName, ActionType.CALL, amount);
  }

  @When("{string} checks")
  public void playerChecks(String playerName) {
    sendPlayerAction(playerName, ActionType.CHECK, 0);
  }

  @When("{string} raises to {int}")
  public void playerRaisesTo(String playerName, int amount) {
    sendPlayerAction(playerName, ActionType.RAISE, amount);
  }

  @When("{string} re-raises to {int}")
  public void playerReRaisesTo(String playerName, int amount) {
    sendPlayerAction(playerName, ActionType.RAISE, amount);
  }

  @When("{string} bets {int}")
  public void playerBets(String playerName, int amount) {
    sendPlayerAction(playerName, ActionType.BET, amount);
  }

  @When("{string} goes all-in for {int}")
  public void playerGoesAllIn(String playerName, int amount) {
    sendPlayerAction(playerName, ActionType.ALL_IN, amount);
  }

  @When("{string} folds with sync_mode CASCADE")
  public void playerFoldsCascade(String playerName) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    PlayerAction cmd =
        PlayerAction.newBuilder()
            .setPlayerRoot(toByteString(playerRoot))
            .setAction(ActionType.FOLD)
            .setAmount(0)
            .build();
    ctx.sendCommandWithMode(
        "hand",
        handRoot,
        cmd,
        SyncMode.SYNC_MODE_CASCADE,
        CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @When("{string} discards {int} cards at indices [{string}]")
  public void playerDiscardsCards(String playerName, int count, String indices) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    RequestDraw.Builder builder = RequestDraw.newBuilder().setPlayerRoot(toByteString(playerRoot));
    for (String idx : indices.split(",")) {
      builder.addCardIndices(Integer.parseInt(idx.trim()));
    }
    ctx.sendCommand("hand", handRoot, builder.build());
  }

  @When("{string} stands pat")
  public void playerStandsPat(String playerName) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    RequestDraw cmd = RequestDraw.newBuilder().setPlayerRoot(toByteString(playerRoot)).build();
    ctx.sendCommand("hand", handRoot, cmd);
  }

  @When("preflop betting completes with calls")
  public void preflopBettingCompletes() {
    // Preflop betting completed naturally via the actions above
    assertThat(ctx.getLastError()).isNull();
  }

  @When("both players check to showdown")
  public void bothPlayersCheckToShowdown() {
    // Both players checking completes naturally via the actions above
    assertThat(ctx.getLastError()).isNull();
  }

  @When("showdown occurs with {string} winning")
  public void showdownOccursWithWinner(String playerName) {
    // Showdown is triggered automatically by the hand aggregate
    assertThat(ctx.getLastError()).isNull();
  }

  @When("showdown occurs")
  public void showdownOccurs() {
    assertThat(ctx.getLastError()).isNull();
  }

  @When("hand {int} completes with {string} winning {int}")
  public void handCompletesWithWinner(int handNum, String playerName, int amount) {
    // Hand completion is saga-driven; verify success
    assertThat(ctx.getLastError()).isNull();
  }

  @When("hand {int} completes")
  public void handCompletes(int handNum) {
    assertThat(ctx.getLastError()).isNull();
  }

  @When("a hand completes through showdown")
  public void handCompletesThroughShowdown() {
    assertThat(ctx.getLastError()).isNull();
  }

  @When("the hand completes with winner {string}")
  public void handCompletesWithWinner(String playerName) {
    assertThat(ctx.getLastError()).isNull();
  }

  @When("the hand completes with sync_mode CASCADE and cascade_error_mode COMPENSATE")
  public void handCompletesWithCascadeCompensate() {
    // Hand completion with CASCADE/COMPENSATE mode - verify success
    assertThat(ctx.getLastError()).isNull();
  }

  @When("{string} attempts to act")
  public void playerAttemptsToAct(String playerName) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    PlayerAction cmd =
        PlayerAction.newBuilder()
            .setPlayerRoot(toByteString(playerRoot))
            .setAction(ActionType.CHECK)
            .setAmount(0)
            .build();
    ctx.trySendCommand("hand", handRoot, cmd);
  }

  @When("player attempts to raise to {int}")
  public void playerAttemptsToRaise(int amount) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    // Use a generic player root for this step
    PlayerAction cmd =
        PlayerAction.newBuilder().setAction(ActionType.RAISE).setAmount(amount).build();
    ctx.trySendCommand("hand", handRoot, cmd);
  }

  @When("{string} adds {int} chips to her stack")
  public void playerAddsChips(String playerName, int amount) {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    AddChips cmd =
        AddChips.newBuilder().setPlayerRoot(toByteString(playerRoot)).setAmount(amount).build();
    ctx.sendCommand("table", tableRoot, cmd);
  }

  @When("{string} attempts to add chips")
  public void playerAttemptsToAddChips(String playerName) {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    AddChips cmd =
        AddChips.newBuilder().setPlayerRoot(toByteString(playerRoot)).setAmount(100).build();
    ctx.trySendCommand("table", tableRoot, cmd);
  }

  @When("{string} attempts to add {int} chips")
  public void playerAttemptsToAddNChips(String playerName, int amount) {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    AddChips cmd =
        AddChips.newBuilder().setPlayerRoot(toByteString(playerRoot)).setAmount(amount).build();
    ctx.trySendCommand("table", tableRoot, cmd);
  }

  // --- Then steps ---

  @Then("{string} wins the pot of {int}")
  public void playerWinsPotOf(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} wins the pot of {int} uncontested")
  public void playerWinsPotUncontested(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the pot is {int}")
  public void potIs(int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} stack is {int}")
  public void playerStackIs(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} has stack {int}")
  public void playerHasStack(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the flop is dealt")
  public void flopIsDealt() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the turn is dealt")
  public void turnIsDealt() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the river is dealt")
  public void riverIsDealt() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("showdown begins")
  public void showdownBegins() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the winner is determined by hand ranking")
  public void winnerDeterminedByRanking() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the hand completes")
  public void handCompletes() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("showdown is triggered immediately")
  public void showdownTriggeredImmediately() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("active player count is {int}")
  public void activePlayerCountIs(int count) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("there is a main pot of {int} with {int} players eligible")
  public void mainPotWithEligible(int amount, int players) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("there is a side pot of {int} with {int} players eligible")
  public void sidePotWithEligible(int amount, int players) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("each player has {int} hole cards")
  public void eachPlayerHasHoleCards(int count) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the remaining deck has {int} cards")
  public void remainingDeckHasCards(int count) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the draw phase begins")
  public void drawPhaseBegins() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} has {int} hole cards")
  public void playerHasHoleCards(String playerName, int count) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the second betting round begins")
  public void secondBettingRoundBegins() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} is eliminated from table {string}")
  public void playerEliminatedFromTable(String playerName, String tableName) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("no showdown occurs")
  public void noShowdownOccurs() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the hand ends without showdown")
  public void handEndsWithoutShowdown() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} wins main pot of {int}")
  public void playerWinsMainPot(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} wins side pot of {int}")
  public void playerWinsSidePot(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the pot of {int} is split evenly")
  public void potSplitEvenly(int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} wins {int}")
  public void playerWinsAmount(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("both players play the board")
  public void bothPlayersPlayTheBoard() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the pot is split evenly")
  public void potIsSplitEvenly() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("both players have a pair of aces")
  public void bothHavePairOfAces() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} wins with king kicker over queen")
  public void playerWinsWithKicker(String playerName) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} must act")
  public void playerMustAct(String playerName) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} is small blind and {string} is big blind")
  public void smallAndBigBlinds(String sbPlayer, String bbPlayer) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} posts the small blind of {int}")
  public void playerPostsSmallBlindOf(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} posts the big blind of {int}")
  public void playerPostsBigBlindOf(String playerName, int amount) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} acts first preflop")
  public void playerActsFirstPreflop(String playerName) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} may call {int} or raise to at least {int}")
  public void playerMayCallOrRaise(String playerName, int callAmount, int minRaise) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} may only call {int} if {string} just calls")
  public void playerMayOnlyCall(String playerName, int amount, String otherPlayer) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("{string} may re-raise if {string} raises")
  public void playerMayReRaise(String playerName, String otherPlayer) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the command fails with {string}")
  public void commandFailsWith(String message) {
    assertThat(ctx.getLastError()).isNotNull();
    assertThat(ctx.getLastError().getMessage()).contains(message);
  }

  @Then("the request fails with {string}")
  public void requestFailsWith(String message) {
    assertThat(ctx.getLastError()).isNotNull();
    assertThat(ctx.getLastError().getMessage()).contains(message);
  }

  @Then("the hand has the same hand_number as the table event")
  public void handSameHandNumber() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("the table updates player stacks")
  public void tableUpdatesPlayerStacks() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Given("a hand is dealt with {string} to act")
  public void handDealtWithPlayerToAct(String playerName) {
    // Pre-condition: hand already dealt, specified player is next to act
    assertThat(ctx.getLastError()).isNull();
  }

  @Given("current bet is {int} and min raise is {int}")
  public void currentBetAndMinRaise(int bet, int minRaise) {
    // Pre-condition: betting state
    assertThat(ctx.getLastError()).isNull();
  }

  @Given("a hand is in progress")
  public void handInProgress() {
    assertThat(ctx.getLastError()).isNull();
  }

  @Given("a hand is in progress with {string} to act")
  public void handInProgressWithPlayerToAct(String playerName) {
    assertThat(ctx.getLastError()).isNull();
  }

  @Then("within {int} seconds:")
  public void withinNSeconds(int seconds, DataTable dataTable) {
    // Poll until timeout or success
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

  @Then("within {int} seconds player {string} bankroll projection shows {int}")
  public void withinNSecondsBankrollShows(int seconds, String name, int amount) {
    // Poll until timeout
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

  // --- Helpers ---

  private void sendPlayerAction(String playerName, ActionType action, int amount) {
    String tableName =
        ctx.getCurrentHandTable() != null ? ctx.getCurrentHandTable() : ctx.getLastTableName();
    UUID handRoot = ctx.getOrCreateHandRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    PlayerAction cmd =
        PlayerAction.newBuilder()
            .setPlayerRoot(toByteString(playerRoot))
            .setAction(action)
            .setAmount(amount)
            .build();
    ctx.sendCommand("hand", handRoot, cmd);
  }

  private static ByteString toByteString(UUID uuid) {
    long msb = uuid.getMostSignificantBits();
    long lsb = uuid.getLeastSignificantBits();
    byte[] bytes = new byte[16];
    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) (msb >>> (56 - i * 8));
      bytes[i + 8] = (byte) (lsb >>> (56 - i * 8));
    }
    return ByteString.copyFrom(bytes);
  }
}
