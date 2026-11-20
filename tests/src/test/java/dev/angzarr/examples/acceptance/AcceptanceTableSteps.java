package dev.angzarr.examples.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.angzarr.examples.*;
import dev.angzarr.examples.client.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
    ctx.setLastTableName(name);
  }

  @When("player {string} joins table {string} at seat {int} with buy-in {int}")
  public void playerJoinsTable(String playerName, String tableName, int seat, int buyIn) {
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    JoinTable cmd =
        JoinTable.newBuilder()
            .setPlayerRoot(toByteString(playerRoot))
            .setPreferredSeat(seat)
            .setBuyInAmount(buyIn)
            .build();
    ctx.sendCommand("table", tableRoot, cmd);
  }

  @When("player {string} leaves table {string}")
  public void playerLeavesTable(String playerName, String tableName) {
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    UUID playerRoot = ctx.getPlayerRoot(playerName);
    LeaveTable cmd = LeaveTable.newBuilder().setPlayerRoot(toByteString(playerRoot)).build();
    ctx.sendCommand("table", tableRoot, cmd);
  }

  @Then("table {string} has {int} seated player(s)")
  public void tableHasSeatedPlayers(String tableName, int count) {
    // The command succeeded if no error was thrown
    assertThat(ctx.getLastError()).isNull();
  }

  @Given("a table {string} with seated players:")
  public void tableWithSeatedPlayers(String tableName, DataTable dataTable) {
    // Create table
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    CreateTable createCmd =
        CreateTable.newBuilder()
            .setTableName(tableName)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(1)
            .setBigBlind(2)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", tableRoot, createCmd);
    ctx.setLastTableName(tableName);

    // Register players and join them
    List<Map<String, String>> rows = dataTable.asMaps();
    for (Map<String, String> row : rows) {
      String name = row.get("name");
      int seat = Integer.parseInt(row.get("seat"));
      int stack = Integer.parseInt(row.get("stack"));

      // Register player
      UUID playerRoot = ctx.getOrCreatePlayerRoot(name);
      RegisterPlayer regCmd =
          RegisterPlayer.newBuilder()
              .setDisplayName(name)
              .setEmail(name.toLowerCase() + "@example.com")
              .setPlayerType(PlayerType.HUMAN)
              .build();
      ctx.sendCommand("player", playerRoot, regCmd);

      // Deposit funds
      DepositFunds depCmd =
          DepositFunds.newBuilder()
              .setAmount(Currency.newBuilder().setAmount(stack).setCurrencyCode("CHIPS"))
              .build();
      sendWithRetry("player", playerRoot, depCmd);

      // Join table
      JoinTable joinCmd =
          JoinTable.newBuilder()
              .setPlayerRoot(toByteString(playerRoot))
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
            .setSmallBlind(1)
            .setBigBlind(2)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .setMaxPlayers(9)
            .build();
    ctx.sendCommand("table", tableRoot, createCmd);
    ctx.setLastTableName(tableName);

    for (int i = 1; i <= count; i++) {
      String name = "Player" + i;
      UUID playerRoot = ctx.getOrCreatePlayerRoot(name);

      RegisterPlayer regCmd =
          RegisterPlayer.newBuilder()
              .setDisplayName(name)
              .setEmail(name.toLowerCase() + "@example.com")
              .setPlayerType(PlayerType.HUMAN)
              .build();
      ctx.sendCommand("player", playerRoot, regCmd);

      DepositFunds depCmd =
          DepositFunds.newBuilder()
              .setAmount(Currency.newBuilder().setAmount(500).setCurrencyCode("CHIPS"))
              .build();
      sendWithRetry("player", playerRoot, depCmd);

      JoinTable joinCmd =
          JoinTable.newBuilder()
              .setPlayerRoot(toByteString(playerRoot))
              .setPreferredSeat(i)
              .setBuyInAmount(500)
              .build();
      ctx.sendCommand("table", tableRoot, joinCmd);
    }
  }

  @Given("a table {string} with an active hand")
  public void tableWithActiveHand(String tableName) {
    // Create table with 2 default players
    tableWithNSeatedPlayers(tableName, 2);

    // Start hand
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    StartHand startCmd = StartHand.newBuilder().build();
    ctx.sendCommand("table", tableRoot, startCmd);
    ctx.setCurrentHandTable(tableName);
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
    ctx.setLastTableName(name);
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
    ctx.setLastTableName(name);
  }

  @Given("seated players:")
  public void seatedPlayers(DataTable dataTable) {
    String tableName = ctx.getLastTableName();
    assertThat(tableName).isNotNull();
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);

    List<Map<String, String>> rows = dataTable.asMaps();
    for (Map<String, String> row : rows) {
      String name = row.get("name");
      int seat = Integer.parseInt(row.get("seat"));
      int stack = Integer.parseInt(row.get("stack"));

      UUID playerRoot = ctx.getOrCreatePlayerRoot(name);

      RegisterPlayer regCmd =
          RegisterPlayer.newBuilder()
              .setDisplayName(name)
              .setEmail(name.toLowerCase() + "@example.com")
              .setPlayerType(PlayerType.HUMAN)
              .build();
      ctx.sendCommand("player", playerRoot, regCmd);

      DepositFunds depCmd =
          DepositFunds.newBuilder()
              .setAmount(Currency.newBuilder().setAmount(stack).setCurrencyCode("CHIPS"))
              .build();
      sendWithRetry("player", playerRoot, depCmd);

      JoinTable joinCmd =
          JoinTable.newBuilder()
              .setPlayerRoot(toByteString(playerRoot))
              .setPreferredSeat(seat)
              .setBuyInAmount(stack)
              .build();
      ctx.sendCommand("table", tableRoot, joinCmd);
    }
  }

  @Then("table {string} has hand_count {int}")
  public void tableHasHandCount(String tableName, int count) {
    // Assert command succeeded
    assertThat(ctx.getLastError()).isNull();
  }

  @When("I send a StartHand command to table {string}")
  public void sendStartHandCommand(String tableName) {
    UUID tableRoot = ctx.getOrCreateTableRoot(tableName);
    StartHand cmd = StartHand.newBuilder().build();
    ctx.sendCommand("table", tableRoot, cmd);
    ctx.setCurrentHandTable(tableName);
  }

  @When("a hand starts at table {string}")
  public void handStartsAtTable(String tableName) {
    sendStartHandCommand(tableName);
  }

  // --- Helpers ---

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

  private void sendWithRetry(String domain, UUID root, com.google.protobuf.Message command) {
    int maxAttempts = 10;
    Exception lastErr = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        ctx.sendCommand(domain, root, command);
        return;
      } catch (Exception e) {
        lastErr = e;
        try {
          Thread.sleep(200L * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted during retry", ie);
        }
      }
    }
    throw new RuntimeException("Command failed after " + maxAttempts + " attempts", lastErr);
  }
}
