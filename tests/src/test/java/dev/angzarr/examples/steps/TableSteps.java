package dev.angzarr.examples.steps;

import static dev.angzarr.examples.steps.CommonSteps.bytesToHex;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.PageHeader;
import dev.angzarr.client.Errors;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.DispatchException;
import dev.angzarr.client.router.Router;
import dev.angzarr.client.util.ByteUtils;
import dev.angzarr.examples.*;
import dev.angzarr.examples.table.Table;
import dev.angzarr.examples.table.state.SeatState;
import dev.angzarr.examples.table.state.TableState;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cucumber step definitions for Table aggregate tests. */
public class TableSteps {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private CommandHandlerRouter<TableState> router;
  private List<EventPage> eventPages;
  private Message resultEvent;
  private Errors.CommandRejectedError rejectedError;

  @Before
  public void setup() {
    @SuppressWarnings("unchecked")
    CommandHandlerRouter<TableState> r =
        (CommandHandlerRouter<TableState>)
            Router.newBuilder("table-test").withHandler(Table.class, Table::new).build();
    router = r;
    eventPages = new ArrayList<>();
    resultEvent = null;
    rejectedError = null;
  }

  private TableState state() {
    return router.rebuildStateFor(Table.class, currentEventBook());
  }

  private EventBook currentEventBook() {
    return EventBook.newBuilder()
        .setCover(Cover.newBuilder().setDomain("table"))
        .addAllPages(eventPages)
        .setNextSequence(eventPages.size())
        .build();
  }

  // --- Given steps ---

  @Given("no prior events for the table aggregate")
  public void noPriorEventsForTable() {
    eventPages.clear();
    rehydrateTable();
  }

  @Given("a TableCreated event for {string}")
  public void tableCreatedEventFor(String name) {
    TableCreated event =
        TableCreated.newBuilder()
            .setTableName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(5)
            .setBigBlind(10)
            .setMaxPlayers(9)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .build();
    addEvent(event);
    rehydrateTable();
  }

  @Given("a TableCreated event for {string} with min_buy_in {int}")
  public void tableCreatedEventWithMinBuyIn(String name, int minBuyIn) {
    TableCreated event =
        TableCreated.newBuilder()
            .setTableName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(5)
            .setBigBlind(10)
            .setMaxPlayers(9)
            .setMinBuyIn(minBuyIn)
            .setMaxBuyIn(1000)
            .build();
    addEvent(event);
    rehydrateTable();
  }

  @Given("a TableCreated event for {string} with max_players {int}")
  public void tableCreatedEventWithMaxPlayers(String name, int maxPlayers) {
    TableCreated event =
        TableCreated.newBuilder()
            .setTableName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setSmallBlind(5)
            .setBigBlind(10)
            .setMaxPlayers(maxPlayers)
            .setMinBuyIn(200)
            .setMaxBuyIn(1000)
            .build();
    addEvent(event);
    rehydrateTable();
  }

  @Given("a PlayerJoined event for player {string} at seat {int}")
  public void playerJoinedEventAtSeat(String playerId, int seat) {
    playerJoinedEventAtSeatWithStack(playerId, seat, 500);
  }

  @Given("a PlayerJoined event for player {string} at seat {int} with stack {int}")
  public void playerJoinedEventAtSeatWithStack(String playerId, int seat, int stack) {
    PlayerJoined event =
        PlayerJoined.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerId.getBytes(StandardCharsets.UTF_8)))
            .setSeatPosition(seat)
            .setBuyInAmount(stack)
            .setStack(stack)
            .build();
    addEvent(event);
    rehydrateTable();
  }

  @Given("a HandStarted event for hand {int}")
  public void handStartedEventForHand(int handNumber) {
    handStartedEventWithDealer(handNumber, 0);
  }

  @Given("a HandStarted event for hand {int} with dealer at seat {int}")
  public void handStartedEventWithDealer(int handNumber, int dealerPosition) {
    HandStarted event =
        HandStarted.newBuilder()
            .setHandNumber(handNumber)
            .setDealerPosition(dealerPosition)
            .setSmallBlind(5)
            .setBigBlind(10)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .build();
    addEvent(event);
    rehydrateTable();
  }

  @Given("a HandEnded event for hand {int}")
  public void handEndedEventForHand(int handNumber) {
    // HandEnded uses hand_root, not hand_number - use a synthetic root
    byte[] handRoot = ("hand_" + handNumber).getBytes(StandardCharsets.UTF_8);
    HandEnded event = HandEnded.newBuilder().setHandRoot(ByteString.copyFrom(handRoot)).build();
    addEvent(event);
    rehydrateTable();
  }

  // --- When steps ---

  @When("I handle a CreateTable command with name {string} and variant {string}:")
  public void handleCreateTableCommand(String name, String variant, DataTable dataTable) {
    Map<String, String> params = dataTable.asMaps().get(0);
    GameVariant gameVariant = GameVariant.valueOf(variant);

    CreateTable cmd =
        CreateTable.newBuilder()
            .setTableName(name)
            .setGameVariant(gameVariant)
            .setSmallBlind(Integer.parseInt(params.get("small_blind")))
            .setBigBlind(Integer.parseInt(params.get("big_blind")))
            .setMinBuyIn(Integer.parseInt(params.get("min_buy_in")))
            .setMaxBuyIn(Integer.parseInt(params.get("max_buy_in")))
            .setMaxPlayers(Integer.parseInt(params.get("max_players")))
            .build();
    handleCommand(cmd);
  }

  @When("I handle a JoinTable command for player {string} at seat {int} with buy-in {int}")
  public void handleJoinTableCommand(String playerId, int seat, int buyIn) {
    JoinTable cmd =
        JoinTable.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerId.getBytes(StandardCharsets.UTF_8)))
            .setPreferredSeat(seat)
            .setBuyInAmount(buyIn)
            .build();
    handleCommand(cmd);
  }

  @When("I handle a LeaveTable command for player {string}")
  public void handleLeaveTableCommand(String playerId) {
    LeaveTable cmd =
        LeaveTable.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerId.getBytes(StandardCharsets.UTF_8)))
            .build();
    handleCommand(cmd);
  }

  @When("I handle a StartHand command")
  public void handleStartHandCommand() {
    StartHand cmd = StartHand.newBuilder().build();
    handleCommand(cmd);
  }

  @When("I handle an EndHand command with winner {string} winning {int}")
  public void handleEndHandCommand(String winnerId, int amount) {
    ByteString winnerRoot = ByteString.copyFrom(winnerId.getBytes(StandardCharsets.UTF_8));
    EndHand cmd =
        EndHand.newBuilder()
            .addResults(
                PotResult.newBuilder()
                    .setWinnerRoot(winnerRoot)
                    .setAmount(amount)
                    .setPotType("main"))
            .build();
    handleCommand(cmd);
  }

  @When("I handle an EndHand command with results:")
  public void handleEndHandCommandWithResults(DataTable dataTable) {
    EndHand.Builder cmdBuilder = EndHand.newBuilder();
    List<Map<String, String>> results = dataTable.asMaps();
    for (Map<String, String> result : results) {
      String playerId = result.get("player");
      int change = Integer.parseInt(result.get("change"));
      // Add all changes to results to track stack_changes for both winners and losers
      ByteString playerRoot = ByteString.copyFrom(playerId.getBytes(StandardCharsets.UTF_8));
      cmdBuilder.addResults(
          PotResult.newBuilder().setWinnerRoot(playerRoot).setAmount(change).setPotType("main"));
    }
    handleCommand(cmdBuilder.build());
  }

  @When("I rebuild the table state")
  public void rebuildTableState() {
    rehydrateTable();
  }

  // --- Then steps ---

  @Then("^the result is a(?:n)? (?:examples\\.)?TableCreated event$")
  public void resultIsTableCreatedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(TableCreated.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?PlayerJoined event$")
  public void resultIsPlayerJoinedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(PlayerJoined.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?PlayerLeft event$")
  public void resultIsPlayerLeftEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(PlayerLeft.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?HandStarted event$")
  public void resultIsHandStartedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(HandStarted.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?HandEnded event$")
  public void resultIsHandEndedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(HandEnded.class);
  }

  @Then("the table event has table_name {string}")
  public void tableEventHasName(String name) {
    assertThat(resultEvent).isInstanceOf(TableCreated.class);
    TableCreated event = (TableCreated) resultEvent;
    assertThat(event.getTableName()).isEqualTo(name);
  }

  @Then("the table event has game_variant {string}")
  public void tableEventHasGameVariant(String variant) {
    GameVariant expected = GameVariant.valueOf(variant);
    if (resultEvent instanceof TableCreated) {
      assertThat(((TableCreated) resultEvent).getGameVariant()).isEqualTo(expected);
    } else if (resultEvent instanceof HandStarted) {
      assertThat(((HandStarted) resultEvent).getGameVariant()).isEqualTo(expected);
    }
  }

  @Then("the table event has small_blind {int}")
  public void tableEventHasSmallBlind(int smallBlind) {
    if (resultEvent instanceof TableCreated) {
      assertThat(((TableCreated) resultEvent).getSmallBlind()).isEqualTo(smallBlind);
    } else if (resultEvent instanceof HandStarted) {
      assertThat(((HandStarted) resultEvent).getSmallBlind()).isEqualTo(smallBlind);
    }
  }

  @Then("the table event has big_blind {int}")
  public void tableEventHasBigBlind(int bigBlind) {
    if (resultEvent instanceof TableCreated) {
      assertThat(((TableCreated) resultEvent).getBigBlind()).isEqualTo(bigBlind);
    } else if (resultEvent instanceof HandStarted) {
      assertThat(((HandStarted) resultEvent).getBigBlind()).isEqualTo(bigBlind);
    }
  }

  @Then("the table event has seat_position {int}")
  public void tableEventHasSeatPosition(int position) {
    assertThat(resultEvent).isInstanceOf(PlayerJoined.class);
    assertThat(((PlayerJoined) resultEvent).getSeatPosition()).isEqualTo(position);
  }

  @Then("the table event has buy_in_amount {int}")
  public void tableEventHasBuyInAmount(int amount) {
    assertThat(resultEvent).isInstanceOf(PlayerJoined.class);
    assertThat(((PlayerJoined) resultEvent).getBuyInAmount()).isEqualTo(amount);
  }

  @Then("the table event has chips_cashed_out {int}")
  public void tableEventHasChipsCashedOut(int amount) {
    assertThat(resultEvent).isInstanceOf(PlayerLeft.class);
    assertThat(((PlayerLeft) resultEvent).getChipsCashedOut()).isEqualTo(amount);
  }

  @Then("the table event has hand_number {int}")
  public void tableEventHasHandNumber(int handNumber) {
    // HandStarted has hand_number, HandEnded only has hand_root
    assertThat(resultEvent).isInstanceOf(HandStarted.class);
    assertThat(((HandStarted) resultEvent).getHandNumber()).isEqualTo(handNumber);
  }

  @Then("the table event has dealer_position {int}")
  public void tableEventHasDealerPosition(int position) {
    assertThat(resultEvent).isInstanceOf(HandStarted.class);
    assertThat(((HandStarted) resultEvent).getDealerPosition()).isEqualTo(position);
  }

  @Then("the table event has {int} active_players")
  public void tableEventHasActivePlayers(int count) {
    assertThat(resultEvent).isInstanceOf(HandStarted.class);
    assertThat(((HandStarted) resultEvent).getActivePlayersCount()).isEqualTo(count);
  }

  @Then("player {string} stack change is {int}")
  public void playerStackChangeIs(String playerId, int change) {
    assertThat(resultEvent).isInstanceOf(HandEnded.class);
    HandEnded event = (HandEnded) resultEvent;
    // The map key is hex-encoded player_root bytes
    String playerHex = ByteUtils.bytesToHex(playerId.getBytes(StandardCharsets.UTF_8));
    long actualChange = event.getStackChangesOrDefault(playerHex, 0L);
    assertThat(actualChange).isEqualTo((long) change);
  }

  @Then("the table state has {int} players")
  public void tableStateHasPlayers(int count) {
    assertThat(state().getPlayerCount()).isEqualTo(count);
  }

  @Then("the table state has seat {int} occupied by {string}")
  public void tableStateHasSeatOccupiedBy(int seat, String playerId) {
    SeatState seatState = state().getSeats().get(seat);
    assertThat(seatState).isNotNull();
    var expectedHex = bytesToHex(playerId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var actualHex = bytesToHex(seatState.getPlayerRoot());
    assertThat(actualHex).isEqualTo(expectedHex);
  }

  @Then("the table state has status {string}")
  public void tableStateHasStatus(String status) {
    assertThat(state().getStatus()).isEqualTo(status);
  }

  @Then("the table state has hand_count {int}")
  public void tableStateHasHandCount(int count) {
    assertThat(state().getHandCount()).isEqualTo(count);
  }

  // Note: "the command fails with status" and "the error message contains"
  // are defined in PlayerSteps.java and shared across all step classes

  // --- Helper methods ---

  private void addEvent(Message event) {
    Any eventAny = Any.pack(event, "type.googleapis.com/");
    EventPage page =
        EventPage.newBuilder()
            .setHeader(PageHeader.newBuilder().setSequence(eventPages.size()).build())
            .setEvent(eventAny)
            .build();
    eventPages.add(page);
  }

  /** No-op: {@link #state()} materializes fresh state from the router each call. */
  private void rehydrateTable() {}

  private void handleCommand(Message command) {
    ContextualCommand ctx =
        ContextualCommand.newBuilder()
            .setCommand(
                CommandBook.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("table"))
                    .addPages(
                        CommandPage.newBuilder().setCommand(Any.pack(command, TYPE_URL_PREFIX))))
            .setEvents(currentEventBook())
            .build();
    try {
      BusinessResponse response = router.dispatch(ctx);
      EventBook emitted = response.getEvents();
      resultEvent =
          emitted.getPagesCount() == 0 ? null : decodeEmittedEvent(emitted.getPages(0).getEvent());
      for (EventPage page : emitted.getPagesList()) {
        eventPages.add(
            EventPage.newBuilder()
                .setHeader(PageHeader.newBuilder().setSequence(eventPages.size()))
                .setEvent(page.getEvent())
                .build());
      }
      rejectedError = null;
      CommonSteps.setLastRejectedError(null);
    } catch (DispatchException de) {
      resultEvent = null;
      rejectedError = unwrapRejection(de);
      CommonSteps.setLastRejectedError(rejectedError);
    }
  }

  private static Errors.CommandRejectedError unwrapRejection(DispatchException de) {
    for (Throwable t = de; t != null; t = t.getCause()) {
      if (t instanceof Errors.CommandRejectedError cre) {
        return cre;
      }
    }
    return new Errors.CommandRejectedError(de.getMessage(), de.code());
  }

  private static Message decodeEmittedEvent(Any any) {
    String typeUrl = any.getTypeUrl();
    String simpleName = typeUrl.substring(typeUrl.lastIndexOf('.') + 1);
    try {
      return switch (simpleName) {
        case "TableCreated" -> TableCreated.parseFrom(any.getValue());
        case "PlayerJoined" -> PlayerJoined.parseFrom(any.getValue());
        case "PlayerLeft" -> PlayerLeft.parseFrom(any.getValue());
        case "PlayerSatOut" -> PlayerSatOut.parseFrom(any.getValue());
        case "PlayerSatIn" -> PlayerSatIn.parseFrom(any.getValue());
        case "HandStarted" -> HandStarted.parseFrom(any.getValue());
        case "HandEnded" -> HandEnded.parseFrom(any.getValue());
        case "ChipsAdded" -> ChipsAdded.parseFrom(any.getValue());
        default -> throw new IllegalStateException("unknown table event type: " + typeUrl);
      };
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw new IllegalStateException("cannot decode " + typeUrl, e);
    }
  }
}
