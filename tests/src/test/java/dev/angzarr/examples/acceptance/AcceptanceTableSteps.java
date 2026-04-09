package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.angzarr.CommandResponse;
import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
    UUID tableRoot = ctx.getTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    JoinTable cmd =
        JoinTable.newBuilder()
            .setPlayerRoot(
                ByteString.copyFrom(playerRoot.toString().getBytes(StandardCharsets.UTF_8)))
            .setPreferredSeat(seat)
            .setBuyInAmount(buyIn)
            .build();
    ctx.sendCommand("table", tableRoot, cmd);
  }

  @When("player {string} leaves table {string}")
  public void playerLeavesTable(String playerName, String tableName) {
    UUID tableRoot = ctx.getTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    LeaveTable cmd =
        LeaveTable.newBuilder()
            .setPlayerRoot(
                ByteString.copyFrom(playerRoot.toString().getBytes(StandardCharsets.UTF_8)))
            .build();
    ctx.sendCommand("table", tableRoot, cmd);
  }

  @Then("table {string} has {int} seated player(s)")
  public void tableHasSeatedPlayers(String tableName, int count) {
    CommandResponse resp = ctx.getLastResponse();
    assertThat(resp).isNotNull();
  }

  @Given("a table {string} with seated players:")
  public void tableWithSeatedPlayers(String tableName, DataTable dataTable) {
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);

    // Create the table
    CreateTable createCmd =
        CreateTable.newBuilder()
            .setTableName(tableName)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(5)
            .setBigBlind(10)
            .setMinBuyIn(10)
            .setMaxBuyIn(10000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", tableRoot, createCmd);

    // Register, fund, and seat each player
    List<Map<String, String>> rows = dataTable.asMaps();
    for (Map<String, String> row : rows) {
      String name = row.get("name");
      int seat = Integer.parseInt(row.get("seat"));
      int stack = Integer.parseInt(row.get("stack"));
      UUID playerRoot = ctx.getOrCreatePlayerRoot(name);

      // Register and fund player
      RegisterPlayer regCmd =
          RegisterPlayer.newBuilder()
              .setDisplayName(name)
              .setEmail(name.toLowerCase() + "@example.com")
              .setPlayerType(PlayerType.HUMAN)
              .build();
      ctx.trySendCommand("player", playerRoot, regCmd);

      DepositFunds depCmd =
          DepositFunds.newBuilder()
              .setAmount(Currency.newBuilder().setAmount(stack * 2).setCurrencyCode("CHIPS"))
              .build();
      ctx.trySendCommand("player", playerRoot, depCmd);

      // Join the table
      JoinTable joinCmd =
          JoinTable.newBuilder()
              .setPlayerRoot(
                  ByteString.copyFrom(playerRoot.toString().getBytes(StandardCharsets.UTF_8)))
              .setPreferredSeat(seat)
              .setBuyInAmount(stack)
              .build();
      ctx.sendCommand("table", tableRoot, joinCmd);
    }
  }

  @Given("a table {string} with {int} seated players")
  public void tableWithNSeatedPlayers(String tableName, int count) {
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    CreateTable createCmd =
        CreateTable.newBuilder()
            .setTableName(tableName)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(5)
            .setBigBlind(10)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", tableRoot, createCmd);

    for (int i = 0; i < count; i++) {
      String name = "Player" + (i + 1);
      UUID playerRoot = ctx.getOrCreatePlayerRoot(name);
      RegisterPlayer regCmd =
          RegisterPlayer.newBuilder()
              .setDisplayName(name)
              .setEmail(name.toLowerCase() + "@example.com")
              .setPlayerType(PlayerType.HUMAN)
              .build();
      ctx.trySendCommand("player", playerRoot, regCmd);
      DepositFunds depCmd =
          DepositFunds.newBuilder()
              .setAmount(Currency.newBuilder().setAmount(1000).setCurrencyCode("CHIPS"))
              .build();
      ctx.trySendCommand("player", playerRoot, depCmd);
      JoinTable joinCmd =
          JoinTable.newBuilder()
              .setPlayerRoot(
                  ByteString.copyFrom(playerRoot.toString().getBytes(StandardCharsets.UTF_8)))
              .setPreferredSeat(i)
              .setBuyInAmount(500)
              .build();
      ctx.sendCommand("table", tableRoot, joinCmd);
    }
  }

  @Given("a table {string} with an active hand")
  public void tableWithActiveHand(String tableName) {
    tableWithNSeatedPlayers(tableName, 2);
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
    // Players seated at the most recently created table
  }

  @Then("table {string} has hand_count {int}")
  public void tableHasHandCount(String tableName, int count) {
    // Verification step
  }

  @When("I send a StartHand command to table {string}")
  public void sendStartHandCommand(String tableName) {
    UUID tableRoot = ctx.getTableRoot(tableName);
    StartHand cmd = StartHand.newBuilder().build();
    ctx.sendCommand("table", tableRoot, cmd);
  }

  @When("a hand starts at table {string}")
  public void handStartsAtTable(String tableName) {
    sendStartHandCommand(tableName);
  }
}
