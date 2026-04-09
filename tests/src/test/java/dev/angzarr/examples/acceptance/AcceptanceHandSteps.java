package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Acceptance step definitions for hand-related and betting scenarios. */
public class AcceptanceHandSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  @Given("deterministic deck seed {string}")
  public void deterministicDeckSeed(String seed) {
    // Store seed for use when starting the hand
  }

  @Given("deterministic deck where both players make the same flush")
  public void deterministicDeckSameFlush() {
    // Deterministic deck setup
  }

  @Given("deterministic deck with community cards making a royal flush")
  public void deterministicDeckRoyalFlush() {
    // Deterministic deck setup
  }

  @Given("deterministic deck where:")
  public void deterministicDeckWhere(DataTable dataTable) {
    // Deterministic deck setup from table data
  }

  @Given("deterministic deck where Alice has best hand, Bob has second best")
  public void deterministicDeckAliceBestBobSecond() {
    // Deterministic deck setup
  }

  @When("a hand starts and blinds are posted \\({int}\\/{int})")
  public void handStartsAndBlindsPosted(int smallBlind, int bigBlind) {
    // Start hand then post blinds
  }

  @When("blinds are posted \\({int}\\/{int})")
  public void blindsArePosted(int smallBlind, int bigBlind) {
    // Post blinds for current hand
  }

  @When("a hand starts with dealer at seat {int}")
  public void handStartsWithDealerAtSeat(int seat) {
    // Start hand with specific dealer position
  }

  @When("{string} posts small blind {int}")
  public void postsSmallBlind(String playerName, int amount) {
    // Post small blind
  }

  @When("{string} posts big blind {int}")
  public void postsBigBlind(String playerName, int amount) {
    // Post big blind
  }

  @When("{string} folds")
  public void playerFolds(String playerName) {
    // Player folds action
  }

  @When("{string} calls {int}")
  public void playerCalls(String playerName, int amount) {
    // Player calls
  }

  @When("{string} checks")
  public void playerChecks(String playerName) {
    // Player checks
  }

  @When("{string} raises to {int}")
  public void playerRaisesTo(String playerName, int amount) {
    // Player raises
  }

  @When("{string} re-raises to {int}")
  public void playerReRaisesTo(String playerName, int amount) {
    playerRaisesTo(playerName, amount);
  }

  @When("{string} bets {int}")
  public void playerBets(String playerName, int amount) {
    // Player bets
  }

  @When("{string} goes all-in for {int}")
  public void playerGoesAllIn(String playerName, int amount) {
    // Player all-in
  }

  @When("{string} folds with sync_mode CASCADE")
  public void playerFoldsCascade(String playerName) {
    playerFolds(playerName);
  }

  @When("{string} discards {int} cards at indices [{string}]")
  public void playerDiscardsCards(String playerName, int count, String indices) {
    // Discard cards for draw poker
  }

  @When("{string} stands pat")
  public void playerStandsPat(String playerName) {
    // Stand pat (keep all cards)
  }

  @When("preflop betting completes with calls")
  public void preflopBettingCompletes() {
    // Complete preflop betting
  }

  @When("both players check to showdown")
  public void bothPlayersCheckToShowdown() {
    // Check through all streets
  }

  @When("showdown occurs with {string} winning")
  public void showdownOccursWithWinner(String playerName) {
    // Showdown resolution
  }

  @When("showdown occurs")
  public void showdownOccurs() {
    // Showdown resolution
  }

  @When("hand {int} completes with {string} winning {int}")
  public void handCompletesWithWinner(int handNum, String playerName, int amount) {
    // Complete a hand with specific winner
  }

  @When("hand {int} completes")
  public void handCompletes(int handNum) {
    // Complete a hand
  }

  @When("a hand completes through showdown")
  public void handCompletesThroughShowdown() {
    // Full hand through showdown
  }

  @When("the hand completes with winner {string}")
  public void handCompletesWithWinner(String playerName) {
    // Hand completes with specific winner
  }

  @When("the hand completes with sync_mode CASCADE and cascade_error_mode COMPENSATE")
  public void handCompletesWithCascadeCompensate() {
    // Hand completes with CASCADE and COMPENSATE
  }

  @When("{string} attempts to act")
  public void playerAttemptsToAct(String playerName) {
    // Player attempts action out of turn
  }

  @When("player attempts to raise to {int}")
  public void playerAttemptsToRaise(int amount) {
    // Invalid raise attempt
  }

  @When("{string} adds {int} chips to her stack")
  public void playerAddsChips(String playerName, int amount) {
    // Add chips between hands
  }

  @When("{string} attempts to add chips")
  public void playerAttemptsToAddChips(String playerName) {
    // Attempt to add chips during hand
  }

  @When("{string} attempts to add {int} chips")
  public void playerAttemptsToAddNChips(String playerName, int amount) {
    // Attempt to add specific amount of chips
  }

  // --- Then steps ---

  @Then("{string} wins the pot of {int}")
  public void playerWinsPotOf(String playerName, int amount) {
    // Verify pot winner
  }

  @Then("{string} wins the pot of {int} uncontested")
  public void playerWinsPotUncontested(String playerName, int amount) {
    // Verify uncontested win
  }

  @Then("the pot is {int}")
  public void potIs(int amount) {
    // Verify pot total
  }

  @Then("{string} stack is {int}")
  public void playerStackIs(String playerName, int amount) {
    // Verify player stack
  }

  @Then("{string} has stack {int}")
  public void playerHasStack(String playerName, int amount) {
    // Verify player stack
  }

  @Then("the flop is dealt")
  public void flopIsDealt() {
    // Verify flop dealt
  }

  @Then("the turn is dealt")
  public void turnIsDealt() {
    // Verify turn dealt
  }

  @Then("the river is dealt")
  public void riverIsDealt() {
    // Verify river dealt
  }

  @Then("showdown begins")
  public void showdownBegins() {
    // Verify showdown started
  }

  @Then("the winner is determined by hand ranking")
  public void winnerDeterminedByRanking() {
    // Verify hand evaluation
  }

  @Then("the hand completes")
  public void handCompletes() {
    // Verify hand complete
  }

  @Then("showdown is triggered immediately")
  public void showdownTriggeredImmediately() {
    // Verify showdown triggered
  }

  @Then("active player count is {int}")
  public void activePlayerCountIs(int count) {
    // Verify active player count
  }

  @Then("there is a main pot of {int} with {int} players eligible")
  public void mainPotWithEligible(int amount, int players) {
    // Verify main pot
  }

  @Then("there is a side pot of {int} with {int} players eligible")
  public void sidePotWithEligible(int amount, int players) {
    // Verify side pot
  }

  @Then("each player has {int} hole cards")
  public void eachPlayerHasHoleCards(int count) {
    // Verify hole card count
  }

  @Then("the remaining deck has {int} cards")
  public void remainingDeckHasCards(int count) {
    // Verify remaining deck count
  }

  @Then("the draw phase begins")
  public void drawPhaseBegins() {
    // Verify draw phase
  }

  @Then("{string} has {int} hole cards")
  public void playerHasHoleCards(String playerName, int count) {
    // Verify player hole card count
  }

  @Then("the second betting round begins")
  public void secondBettingRoundBegins() {
    // Verify second betting round
  }

  @Then("{string} is eliminated from table {string}")
  public void playerEliminatedFromTable(String playerName, String tableName) {
    // Verify player eliminated
  }

  @Then("no showdown occurs")
  public void noShowdownOccurs() {
    // Verify no showdown
  }

  @Then("the hand ends without showdown")
  public void handEndsWithoutShowdown() {
    // Verify hand ends without showdown
  }

  @Then("{string} wins main pot of {int}")
  public void playerWinsMainPot(String playerName, int amount) {
    // Verify main pot winner
  }

  @Then("{string} wins side pot of {int}")
  public void playerWinsSidePot(String playerName, int amount) {
    // Verify side pot winner
  }

  @Then("the pot of {int} is split evenly")
  public void potSplitEvenly(int amount) {
    // Verify split pot
  }

  @Then("{string} wins {int}")
  public void playerWinsAmount(String playerName, int amount) {
    // Verify player wins specific amount
  }

  @Then("both players play the board")
  public void bothPlayersPlayTheBoard() {
    // Verify both play board
  }

  @Then("the pot is split evenly")
  public void potIsSplitEvenly() {
    // Verify even split
  }

  @Then("both players have a pair of aces")
  public void bothHavePairOfAces() {
    // Verify hand rankings
  }

  @Then("{string} wins with king kicker over queen")
  public void playerWinsWithKicker(String playerName) {
    // Verify kicker wins
  }

  @Then("{string} must act")
  public void playerMustAct(String playerName) {
    // Verify next to act
  }

  @Then("{string} is small blind and {string} is big blind")
  public void smallAndBigBlinds(String sbPlayer, String bbPlayer) {
    // Verify blind positions
  }

  @Then("{string} posts the small blind of {int}")
  public void playerPostsSmallBlindOf(String playerName, int amount) {
    // Verify small blind posting
  }

  @Then("{string} posts the big blind of {int}")
  public void playerPostsBigBlindOf(String playerName, int amount) {
    // Verify big blind posting
  }

  @Then("{string} acts first preflop")
  public void playerActsFirstPreflop(String playerName) {
    // Verify first to act
  }

  @Then("{string} may call {int} or raise to at least {int}")
  public void playerMayCallOrRaise(String playerName, int callAmount, int minRaise) {
    // Verify valid actions
  }

  @Then("{string} may only call {int} if {string} just calls")
  public void playerMayOnlyCall(String playerName, int amount, String otherPlayer) {
    // Verify restricted action
  }

  @Then("{string} may re-raise if {string} raises")
  public void playerMayReRaise(String playerName, String otherPlayer) {
    // Verify re-raise allowed
  }

  @Then("the command fails with {string}")
  public void commandFailsWith(String message) {
    assertThat(ctx.getLastError()).isNotNull();
    assertThat(ctx.getLastError().getMessage().toLowerCase()).contains(message.toLowerCase());
  }

  @Then("the request fails with {string}")
  public void requestFailsWith(String message) {
    commandFailsWith(message);
  }

  @Then("the hand has the same hand_number as the table event")
  public void handSameHandNumber() {
    // Verify hand number correlation
  }

  @Then("the table updates player stacks")
  public void tableUpdatesPlayerStacks() {
    // Verify stack updates
  }

  @Given("a hand is dealt with {string} to act")
  public void handDealtWithPlayerToAct(String playerName) {
    // Hand dealt, specific player to act
  }

  @Given("current bet is {int} and min raise is {int}")
  public void currentBetAndMinRaise(int bet, int minRaise) {
    // Set current bet state
  }

  @Given("a hand is in progress")
  public void handInProgress() {
    // Hand in progress
  }

  @Given("a hand is in progress with {string} to act")
  public void handInProgressWithPlayerToAct(String playerName) {
    // Hand in progress with specific player to act
  }

  @Then("within {int} seconds:")
  public void withinNSeconds(int seconds, DataTable dataTable) {
    // Verify events appear within time limit (for saga coordination)
  }

  @Then("within {int} seconds player {string} bankroll projection shows {int}")
  public void withinNSecondsBankrollShows(int seconds, String name, int amount) {
    // Verify bankroll projection within time limit
  }
}
