package dev.angzarr.examples.acceptance;

import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;

/** Acceptance step definitions for table-related scenarios. */
public class AcceptanceTableSteps {

  private final TestContext ctx = AcceptanceHooks.CONTEXT;

  @When("I create a Texas Hold'em table {string} with blinds {int}\\/{int}")
  public void createTexasHoldemTable(String name, int smallBlind, int bigBlind) {
    UUID root = ctx.getOrCreateTableRoot(name);
    CreateTable cmd =
        CreateTable.newBuilder()
            .setTableName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(smallBlind)
            .setBigBlind(bigBlind)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", root, cmd);
  }

  @When("player {string} joins table {string} at seat {int} with buy-in {int}")
  public void playerJoinsTable(String playerName, String tableName, int seat, int buyIn) {
    throw new PendingException();
  }

  @When("player {string} leaves table {string}")
  public void playerLeavesTable(String playerName, String tableName) {
    throw new PendingException();
  }

  @Then("table {string} has {int} seated player(s)")
  public void tableHasSeatedPlayers(String tableName, int count) {
    throw new PendingException();
  }

  @Given("a table {string} with seated players:")
  public void tableWithSeatedPlayers(String tableName, DataTable dataTable) {
    throw new PendingException();
  }

  @Given("a table {string} with {int} seated players")
  public void tableWithNSeatedPlayers(String tableName, int count) {
    throw new PendingException();
  }

  @Given("a table {string} with an active hand")
  public void tableWithActiveHand(String tableName) {
    throw new PendingException();
  }

  @Given("a Five Card Draw table {string} with blinds {int}\\/{int}")
  public void fiveCardDrawTable(String name, int smallBlind, int bigBlind) {
    UUID root = ctx.getOrCreateTableRoot(name);
    CreateTable cmd =
        CreateTable.newBuilder()
            .setTableName(name)
            .setGameVariant(GameVariant.FIVE_CARD_DRAW)
            .setSmallBlind(smallBlind)
            .setBigBlind(bigBlind)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", root, cmd);
  }

  @Given("an Omaha table {string} with blinds {int}\\/{int}")
  public void omahaTable(String name, int smallBlind, int bigBlind) {
    UUID root = ctx.getOrCreateTableRoot(name);
    CreateTable cmd =
        CreateTable.newBuilder()
            .setTableName(name)
            .setGameVariant(GameVariant.OMAHA)
            .setSmallBlind(smallBlind)
            .setBigBlind(bigBlind)
            .setMinBuyIn(200)
            .setMaxBuyIn(2000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", root, cmd);
  }

  @Given("seated players:")
  public void seatedPlayers(DataTable dataTable) {
    throw new PendingException();
  }

  @Then("table {string} has hand_count {int}")
  public void tableHasHandCount(String tableName, int count) {
    throw new PendingException();
  }

  @When("I send a StartHand command to table {string}")
  public void sendStartHandCommand(String tableName) {
    throw new PendingException();
  }

  @When("a hand starts at table {string}")
  public void handStartsAtTable(String tableName) {
    throw new PendingException();
  }
}
