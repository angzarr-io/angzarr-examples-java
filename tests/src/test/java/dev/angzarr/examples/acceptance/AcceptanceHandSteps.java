package dev.angzarr.examples.acceptance;

import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Acceptance step definitions for hand-related and betting scenarios. */
public class AcceptanceHandSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  @Given("deterministic deck seed {string}")
  public void deterministicDeckSeed(String seed) {
    throw new PendingException();
  }

  @Given("deterministic deck where both players make the same flush")
  public void deterministicDeckSameFlush() {
    throw new PendingException();
  }

  @Given("deterministic deck with community cards making a royal flush")
  public void deterministicDeckRoyalFlush() {
    throw new PendingException();
  }

  @Given("deterministic deck where:")
  public void deterministicDeckWhere(DataTable dataTable) {
    throw new PendingException();
  }

  @Given("deterministic deck where Alice has best hand, Bob has second best")
  public void deterministicDeckAliceBestBobSecond() {
    throw new PendingException();
  }

  @When("a hand starts and blinds are posted \\({int}\\/{int})")
  public void handStartsAndBlindsPosted(int smallBlind, int bigBlind) {
    throw new PendingException();
  }

  @When("blinds are posted \\({int}\\/{int})")
  public void blindsArePosted(int smallBlind, int bigBlind) {
    throw new PendingException();
  }

  @When("a hand starts with dealer at seat {int}")
  public void handStartsWithDealerAtSeat(int seat) {
    throw new PendingException();
  }

  @When("{string} posts small blind {int}")
  public void postsSmallBlind(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} posts big blind {int}")
  public void postsBigBlind(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} folds")
  public void playerFolds(String playerName) {
    throw new PendingException();
  }

  @When("{string} calls {int}")
  public void playerCalls(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} checks")
  public void playerChecks(String playerName) {
    throw new PendingException();
  }

  @When("{string} raises to {int}")
  public void playerRaisesTo(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} re-raises to {int}")
  public void playerReRaisesTo(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} bets {int}")
  public void playerBets(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} goes all-in for {int}")
  public void playerGoesAllIn(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} folds with sync_mode CASCADE")
  public void playerFoldsCascade(String playerName) {
    throw new PendingException();
  }

  @When("{string} discards {int} cards at indices [{string}]")
  public void playerDiscardsCards(String playerName, int count, String indices) {
    throw new PendingException();
  }

  @When("{string} stands pat")
  public void playerStandsPat(String playerName) {
    throw new PendingException();
  }

  @When("preflop betting completes with calls")
  public void preflopBettingCompletes() {
    throw new PendingException();
  }

  @When("both players check to showdown")
  public void bothPlayersCheckToShowdown() {
    throw new PendingException();
  }

  @When("showdown occurs with {string} winning")
  public void showdownOccursWithWinner(String playerName) {
    throw new PendingException();
  }

  @When("showdown occurs")
  public void showdownOccurs() {
    throw new PendingException();
  }

  @When("hand {int} completes with {string} winning {int}")
  public void handCompletesWithWinner(int handNum, String playerName, int amount) {
    throw new PendingException();
  }

  @When("hand {int} completes")
  public void handCompletes(int handNum) {
    throw new PendingException();
  }

  @When("a hand completes through showdown")
  public void handCompletesThroughShowdown() {
    throw new PendingException();
  }

  @When("the hand completes with winner {string}")
  public void handCompletesWithWinner(String playerName) {
    throw new PendingException();
  }

  @When("the hand completes with sync_mode CASCADE and cascade_error_mode COMPENSATE")
  public void handCompletesWithCascadeCompensate() {
    throw new PendingException();
  }

  @When("{string} attempts to act")
  public void playerAttemptsToAct(String playerName) {
    throw new PendingException();
  }

  @When("player attempts to raise to {int}")
  public void playerAttemptsToRaise(int amount) {
    throw new PendingException();
  }

  @When("{string} adds {int} chips to her stack")
  public void playerAddsChips(String playerName, int amount) {
    throw new PendingException();
  }

  @When("{string} attempts to add chips")
  public void playerAttemptsToAddChips(String playerName) {
    throw new PendingException();
  }

  @When("{string} attempts to add {int} chips")
  public void playerAttemptsToAddNChips(String playerName, int amount) {
    throw new PendingException();
  }

  // --- Then steps ---

  @Then("{string} wins the pot of {int}")
  public void playerWinsPotOf(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("{string} wins the pot of {int} uncontested")
  public void playerWinsPotUncontested(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("the pot is {int}")
  public void potIs(int amount) {
    throw new PendingException();
  }

  @Then("{string} stack is {int}")
  public void playerStackIs(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("{string} has stack {int}")
  public void playerHasStack(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("the flop is dealt")
  public void flopIsDealt() {
    throw new PendingException();
  }

  @Then("the turn is dealt")
  public void turnIsDealt() {
    throw new PendingException();
  }

  @Then("the river is dealt")
  public void riverIsDealt() {
    throw new PendingException();
  }

  @Then("showdown begins")
  public void showdownBegins() {
    throw new PendingException();
  }

  @Then("the winner is determined by hand ranking")
  public void winnerDeterminedByRanking() {
    throw new PendingException();
  }

  @Then("the hand completes")
  public void handCompletes() {
    throw new PendingException();
  }

  @Then("showdown is triggered immediately")
  public void showdownTriggeredImmediately() {
    throw new PendingException();
  }

  @Then("active player count is {int}")
  public void activePlayerCountIs(int count) {
    throw new PendingException();
  }

  @Then("there is a main pot of {int} with {int} players eligible")
  public void mainPotWithEligible(int amount, int players) {
    throw new PendingException();
  }

  @Then("there is a side pot of {int} with {int} players eligible")
  public void sidePotWithEligible(int amount, int players) {
    throw new PendingException();
  }

  @Then("each player has {int} hole cards")
  public void eachPlayerHasHoleCards(int count) {
    throw new PendingException();
  }

  @Then("the remaining deck has {int} cards")
  public void remainingDeckHasCards(int count) {
    throw new PendingException();
  }

  @Then("the draw phase begins")
  public void drawPhaseBegins() {
    throw new PendingException();
  }

  @Then("{string} has {int} hole cards")
  public void playerHasHoleCards(String playerName, int count) {
    throw new PendingException();
  }

  @Then("the second betting round begins")
  public void secondBettingRoundBegins() {
    throw new PendingException();
  }

  @Then("{string} is eliminated from table {string}")
  public void playerEliminatedFromTable(String playerName, String tableName) {
    throw new PendingException();
  }

  @Then("no showdown occurs")
  public void noShowdownOccurs() {
    throw new PendingException();
  }

  @Then("the hand ends without showdown")
  public void handEndsWithoutShowdown() {
    throw new PendingException();
  }

  @Then("{string} wins main pot of {int}")
  public void playerWinsMainPot(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("{string} wins side pot of {int}")
  public void playerWinsSidePot(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("the pot of {int} is split evenly")
  public void potSplitEvenly(int amount) {
    throw new PendingException();
  }

  @Then("{string} wins {int}")
  public void playerWinsAmount(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("both players play the board")
  public void bothPlayersPlayTheBoard() {
    throw new PendingException();
  }

  @Then("the pot is split evenly")
  public void potIsSplitEvenly() {
    throw new PendingException();
  }

  @Then("both players have a pair of aces")
  public void bothHavePairOfAces() {
    throw new PendingException();
  }

  @Then("{string} wins with king kicker over queen")
  public void playerWinsWithKicker(String playerName) {
    throw new PendingException();
  }

  @Then("{string} must act")
  public void playerMustAct(String playerName) {
    throw new PendingException();
  }

  @Then("{string} is small blind and {string} is big blind")
  public void smallAndBigBlinds(String sbPlayer, String bbPlayer) {
    throw new PendingException();
  }

  @Then("{string} posts the small blind of {int}")
  public void playerPostsSmallBlindOf(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("{string} posts the big blind of {int}")
  public void playerPostsBigBlindOf(String playerName, int amount) {
    throw new PendingException();
  }

  @Then("{string} acts first preflop")
  public void playerActsFirstPreflop(String playerName) {
    throw new PendingException();
  }

  @Then("{string} may call {int} or raise to at least {int}")
  public void playerMayCallOrRaise(String playerName, int callAmount, int minRaise) {
    throw new PendingException();
  }

  @Then("{string} may only call {int} if {string} just calls")
  public void playerMayOnlyCall(String playerName, int amount, String otherPlayer) {
    throw new PendingException();
  }

  @Then("{string} may re-raise if {string} raises")
  public void playerMayReRaise(String playerName, String otherPlayer) {
    throw new PendingException();
  }

  @Then("the command fails with {string}")
  public void commandFailsWith(String message) {
    throw new PendingException();
  }

  @Then("the request fails with {string}")
  public void requestFailsWith(String message) {
    throw new PendingException();
  }

  @Then("the hand has the same hand_number as the table event")
  public void handSameHandNumber() {
    throw new PendingException();
  }

  @Then("the table updates player stacks")
  public void tableUpdatesPlayerStacks() {
    throw new PendingException();
  }

  @Given("a hand is dealt with {string} to act")
  public void handDealtWithPlayerToAct(String playerName) {
    throw new PendingException();
  }

  @Given("current bet is {int} and min raise is {int}")
  public void currentBetAndMinRaise(int bet, int minRaise) {
    throw new PendingException();
  }

  @Given("a hand is in progress")
  public void handInProgress() {
    throw new PendingException();
  }

  @Given("a hand is in progress with {string} to act")
  public void handInProgressWithPlayerToAct(String playerName) {
    throw new PendingException();
  }

  @Then("within {int} seconds:")
  public void withinNSeconds(int seconds, DataTable dataTable) {
    throw new PendingException();
  }

  @Then("within {int} seconds player {string} bankroll projection shows {int}")
  public void withinNSecondsBankrollShows(int seconds, String name, int amount) {
    throw new PendingException();
  }
}
