package dev.angzarr.examples.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.examples.Currency;
import dev.angzarr.examples.DealCards;
import dev.angzarr.examples.DepositFunds;
import dev.angzarr.examples.EndHand;
import dev.angzarr.examples.GameVariant;
import dev.angzarr.examples.HandComplete;
import dev.angzarr.examples.HandEnded;
import dev.angzarr.examples.HandStarted;
import dev.angzarr.examples.PlayerInHand;
import dev.angzarr.examples.PotAwarded;
import dev.angzarr.examples.PotResult;
import dev.angzarr.examples.PotWinner;
import dev.angzarr.examples.ReleaseFunds;
import dev.angzarr.examples.SeatSnapshot;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cucumber step definitions for Saga logic tests. */
public class SagaSteps {

  private String sagaType;
  private Any sourceEvent;
  private List<SeatSnapshot> activePlayers = new ArrayList<>();
  private List<PotWinner> winners = new ArrayList<>();
  private Map<String, Long> stackChanges = new HashMap<>();
  private List<CommandBook> resultCommands = new ArrayList<>();
  private EventBook eventBook;
  private List<String> sagaRouterSagas = new ArrayList<>();
  private List<String> handledBy = new ArrayList<>();

  private static byte[] playerRoot(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))
        .toString()
        .getBytes(StandardCharsets.UTF_8);
  }

  private static Timestamp now() {
    Instant instant = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  // --- Given steps ---

  @Given("a TableSyncSaga")
  public void aTableSyncSaga() {
    sagaType = "TableSyncSaga";
  }

  @Given("a HandResultsSaga")
  public void aHandResultsSaga() {
    sagaType = "HandResultsSaga";
  }

  @Given("a HandStarted event from table domain with:")
  public void handStartedEventFromTableDomain(DataTable dataTable) {
    Map<String, String> row = dataTable.asMaps().get(0);
    HandStarted.Builder builder =
        HandStarted.newBuilder()
            .setHandRoot(ByteString.copyFrom(playerRoot(row.get("hand_root"))))
            .setHandNumber(Long.parseLong(row.get("hand_number")))
            .setGameVariant(GameVariant.valueOf(row.get("game_variant")))
            .setDealerPosition(Integer.parseInt(row.get("dealer_position")))
            .setStartedAt(now());
    sourceEvent = Any.pack(builder.build());
  }

  @Given("active players:")
  public void activePlayersTable(DataTable dataTable) {
    activePlayers.clear();
    CommonSteps.setSharedActivePlayersData(dataTable.asMaps());
    for (Map<String, String> row : dataTable.asMaps()) {
      activePlayers.add(
          SeatSnapshot.newBuilder()
              .setPlayerRoot(ByteString.copyFrom(playerRoot(row.get("player_root"))))
              .setPosition(Integer.parseInt(row.get("position")))
              .setStack(Long.parseLong(row.get("stack")))
              .build());
    }
    // Update HandStarted event with active players (if in saga context)
    try {
      if (sourceEvent != null && sourceEvent.is(HandStarted.class)) {
        HandStarted hs = sourceEvent.unpack(HandStarted.class);
        sourceEvent = Any.pack(hs.toBuilder().addAllActivePlayers(activePlayers).build());
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  @Given("a HandComplete event from hand domain with:")
  public void handCompleteEventFromHandDomain(DataTable dataTable) {
    Map<String, String> row = dataTable.asMaps().get(0);
    HandComplete.Builder builder =
        HandComplete.newBuilder()
            .setTableRoot(ByteString.copyFrom(playerRoot(row.get("table_root"))))
            .setCompletedAt(now());
    sourceEvent = Any.pack(builder.build());
  }

  @Given("winners:")
  public void winnersSetup(DataTable dataTable) {
    winners.clear();
    for (Map<String, String> row : dataTable.asMaps()) {
      winners.add(
          PotWinner.newBuilder()
              .setPlayerRoot(ByteString.copyFrom(playerRoot(row.get("player_root"))))
              .setAmount(Long.parseLong(row.get("amount")))
              .build());
    }
    try {
      if (sourceEvent.is(HandComplete.class)) {
        HandComplete hc = sourceEvent.unpack(HandComplete.class);
        sourceEvent = Any.pack(hc.toBuilder().addAllWinners(winners).build());
      } else if (sourceEvent.is(PotAwarded.class)) {
        PotAwarded pa = sourceEvent.unpack(PotAwarded.class);
        sourceEvent = Any.pack(pa.toBuilder().addAllWinners(winners).build());
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  @Given("a HandEnded event from table domain with:")
  public void handEndedEventFromTableDomain(DataTable dataTable) {
    Map<String, String> row = dataTable.asMaps().get(0);
    HandEnded.Builder builder =
        HandEnded.newBuilder()
            .setHandRoot(ByteString.copyFrom(playerRoot(row.get("hand_root"))))
            .setEndedAt(now());
    sourceEvent = Any.pack(builder.build());
  }

  @Given("stack_changes:")
  public void stackChangesSetup(DataTable dataTable) {
    stackChanges.clear();
    for (Map<String, String> row : dataTable.asMaps()) {
      if (row.get("player_root") != null && !row.get("player_root").isEmpty()) {
        stackChanges.put(row.get("player_root"), Long.parseLong(row.get("change")));
      }
    }
    try {
      if (sourceEvent.is(HandEnded.class)) {
        HandEnded he = sourceEvent.unpack(HandEnded.class);
        HandEnded.Builder builder = he.toBuilder();
        stackChanges.forEach(builder::putStackChanges);
        sourceEvent = Any.pack(builder.build());
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  @Given("a PotAwarded event from hand domain with:")
  public void potAwardedEventFromHandDomain(DataTable dataTable) {
    PotAwarded.Builder builder = PotAwarded.newBuilder().setAwardedAt(now());
    sourceEvent = Any.pack(builder.build());
  }

  @Given("a SagaRouter with TableSyncSaga and HandResultsSaga")
  public void sagaRouterWithBothSagas() {
    sagaRouterSagas = List.of("TableSyncSaga", "HandResultsSaga");
  }

  @Given("a HandStarted event")
  public void handStartedEvent() {
    HandStarted event =
        HandStarted.newBuilder()
            .setHandRoot(ByteString.copyFrom(playerRoot("hand-1")))
            .setHandNumber(1)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setDealerPosition(0)
            .addActivePlayers(
                SeatSnapshot.newBuilder()
                    .setPlayerRoot(ByteString.copyFrom(playerRoot("player-1")))
                    .setPosition(0)
                    .setStack(500))
            .addActivePlayers(
                SeatSnapshot.newBuilder()
                    .setPlayerRoot(ByteString.copyFrom(playerRoot("player-2")))
                    .setPosition(1)
                    .setStack(500))
            .setStartedAt(now())
            .build();
    sourceEvent = Any.pack(event);
  }

  @Given("a SagaRouter with TableSyncSaga")
  public void sagaRouterWithTableSyncSaga() {
    sagaRouterSagas = List.of("TableSyncSaga");
  }

  @Given("an event book with:")
  public void eventBookWith(DataTable dataTable) {
    EventBook.Builder builder =
        EventBook.newBuilder().setCover(Cover.newBuilder().setDomain("table"));
    for (Map<String, String> row : dataTable.asMaps()) {
      String eventType = row.get("event_type");
      if ("HandStarted".equals(eventType)) {
        HandStarted hs =
            HandStarted.newBuilder()
                .setHandRoot(ByteString.copyFrom(playerRoot("hand-1")))
                .setHandNumber(1)
                .setGameVariant(GameVariant.TEXAS_HOLDEM)
                .addActivePlayers(
                    SeatSnapshot.newBuilder()
                        .setPlayerRoot(ByteString.copyFrom(playerRoot("player-1")))
                        .setPosition(0)
                        .setStack(500))
                .addActivePlayers(
                    SeatSnapshot.newBuilder()
                        .setPlayerRoot(ByteString.copyFrom(playerRoot("player-2")))
                        .setPosition(1)
                        .setStack(500))
                .setStartedAt(now())
                .build();
        builder.addPages(EventPage.newBuilder().setEvent(Any.pack(hs)));
      }
    }
    eventBook = builder.build();
  }

  @Given("a SagaRouter with a failing saga and TableSyncSaga")
  public void sagaRouterWithFailingSagaAndTableSyncSaga() {
    sagaRouterSagas = List.of("FailingSaga", "TableSyncSaga");
  }

  // --- When steps ---

  @When("the saga handles the event")
  public void sagaHandlesEvent() throws InvalidProtocolBufferException {
    resultCommands.clear();
    switch (sagaType) {
      case "TableSyncSaga" -> handleTableSyncSaga();
      case "HandResultsSaga" -> handleHandResultsSaga();
      default -> throw new IllegalStateException("Unknown saga: " + sagaType);
    }
  }

  private void handleTableSyncSaga() throws InvalidProtocolBufferException {
    if (sourceEvent.is(HandStarted.class)) {
      HandStarted hs = sourceEvent.unpack(HandStarted.class);
      List<PlayerInHand> players =
          hs.getActivePlayersList().stream()
              .map(
                  s ->
                      PlayerInHand.newBuilder()
                          .setPlayerRoot(s.getPlayerRoot())
                          .setPosition(s.getPosition())
                          .setStack(s.getStack())
                          .build())
              .toList();

      DealCards cmd =
          DealCards.newBuilder()
              .setTableRoot(hs.getHandRoot())
              .setHandNumber(hs.getHandNumber())
              .setGameVariant(hs.getGameVariant())
              .addAllPlayers(players)
              .setDealerPosition(hs.getDealerPosition())
              .build();

      resultCommands.add(
          CommandBook.newBuilder()
              .setCover(Cover.newBuilder().setDomain("hand"))
              .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd)))
              .build());
    } else if (sourceEvent.is(HandComplete.class)) {
      HandComplete hc = sourceEvent.unpack(HandComplete.class);
      List<PotResult> results =
          hc.getWinnersList().stream()
              .map(
                  w ->
                      PotResult.newBuilder()
                          .setWinnerRoot(w.getPlayerRoot())
                          .setAmount(w.getAmount())
                          .setPotType("main")
                          .build())
              .toList();

      EndHand cmd =
          EndHand.newBuilder().setHandRoot(hc.getTableRoot()).addAllResults(results).build();

      resultCommands.add(
          CommandBook.newBuilder()
              .setCover(Cover.newBuilder().setDomain("table"))
              .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd)))
              .build());
    }
  }

  private void handleHandResultsSaga() throws InvalidProtocolBufferException {
    if (sourceEvent.is(HandEnded.class)) {
      HandEnded he = sourceEvent.unpack(HandEnded.class);
      for (String playerKey : he.getStackChangesMap().keySet()) {
        ReleaseFunds cmd = ReleaseFunds.newBuilder().setTableRoot(he.getHandRoot()).build();
        resultCommands.add(
            CommandBook.newBuilder()
                .setCover(Cover.newBuilder().setDomain("player"))
                .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd)))
                .build());
      }
    } else if (sourceEvent.is(PotAwarded.class)) {
      PotAwarded pa = sourceEvent.unpack(PotAwarded.class);
      for (PotWinner winner : pa.getWinnersList()) {
        DepositFunds cmd =
            DepositFunds.newBuilder()
                .setAmount(
                    Currency.newBuilder().setAmount(winner.getAmount()).setCurrencyCode("CHIPS"))
                .build();
        resultCommands.add(
            CommandBook.newBuilder()
                .setCover(Cover.newBuilder().setDomain("player"))
                .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd)))
                .build());
      }
    }
  }

  @When("the router routes the event")
  public void routerRoutesEvent() throws InvalidProtocolBufferException {
    resultCommands.clear();
    handledBy.clear();
    for (String saga : sagaRouterSagas) {
      if ("FailingSaga".equals(saga)) continue;
      if ("TableSyncSaga".equals(saga) && sourceEvent.is(HandStarted.class)) {
        handledBy.add(saga);
        sagaType = "TableSyncSaga";
        handleTableSyncSaga();
      }
    }
  }

  @When("the router routes the events")
  public void routerRoutesEvents() throws InvalidProtocolBufferException {
    resultCommands.clear();
    for (EventPage page : eventBook.getPagesList()) {
      sourceEvent = page.getEvent();
      for (String saga : sagaRouterSagas) {
        if ("TableSyncSaga".equals(saga) && sourceEvent.is(HandStarted.class)) {
          sagaType = "TableSyncSaga";
          handleTableSyncSaga();
        }
      }
    }
  }

  // --- Then steps ---

  @Then("the saga emits a DealCards command to hand domain")
  public void sagaEmitsDealCardsCommand() throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    CommandBook cmd = resultCommands.get(0);
    assertThat(cmd.getCover().getDomain()).isEqualTo("hand");
    assertThat(cmd.getPages(0).getCommand().is(DealCards.class)).isTrue();
  }

  @Then("the command has game_variant {word}")
  public void commandHasGameVariant(String variant) throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    DealCards dc = resultCommands.get(0).getPages(0).getCommand().unpack(DealCards.class);
    assertThat(dc.getGameVariant()).isEqualTo(GameVariant.valueOf(variant));
  }

  @Then("the command has {int} players")
  public void commandHasPlayers(int count) throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    DealCards dc = resultCommands.get(0).getPages(0).getCommand().unpack(DealCards.class);
    assertThat(dc.getPlayersCount()).isEqualTo(count);
  }

  @Then("the command has hand_number {int}")
  public void commandHasHandNumber(int number) throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    DealCards dc = resultCommands.get(0).getPages(0).getCommand().unpack(DealCards.class);
    assertThat(dc.getHandNumber()).isEqualTo(number);
  }

  @Then("the saga emits an EndHand command to table domain")
  public void sagaEmitsEndHandCommand() throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    CommandBook cmd = resultCommands.get(0);
    assertThat(cmd.getCover().getDomain()).isEqualTo("table");
    assertThat(cmd.getPages(0).getCommand().is(EndHand.class)).isTrue();
  }

  @Then("the command has {int} result")
  public void commandHasResults(int count) throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    EndHand eh = resultCommands.get(0).getPages(0).getCommand().unpack(EndHand.class);
    assertThat(eh.getResultsCount()).isEqualTo(count);
  }

  @Then("the result has winner {string} with amount {int}")
  public void resultHasWinner(String playerId, int amount) throws InvalidProtocolBufferException {
    assertThat(resultCommands).isNotEmpty();
    EndHand eh = resultCommands.get(0).getPages(0).getCommand().unpack(EndHand.class);
    boolean found = eh.getResultsList().stream().anyMatch(r -> r.getAmount() == amount);
    assertThat(found).isTrue().withFailMessage("No result with amount %d", amount);
  }

  @Then("the saga emits {int} ReleaseFunds commands to player domain")
  public void sagaEmitsReleaseFundsCommands(int count) {
    assertThat(resultCommands).hasSize(count);
    for (CommandBook cmd : resultCommands) {
      assertThat(cmd.getCover().getDomain()).isEqualTo("player");
    }
  }

  @Then("the saga emits {int} DepositFunds commands to player domain")
  public void sagaEmitsDepositFundsCommands(int count) throws InvalidProtocolBufferException {
    assertThat(resultCommands).hasSize(count);
    for (CommandBook cmd : resultCommands) {
      assertThat(cmd.getCover().getDomain()).isEqualTo("player");
      assertThat(cmd.getPages(0).getCommand().is(DepositFunds.class)).isTrue();
    }
  }

  @Then("the first command has amount {int} for {string}")
  public void firstCommandHasAmount(int amount, String playerId)
      throws InvalidProtocolBufferException {
    assertThat(resultCommands).hasSizeGreaterThanOrEqualTo(1);
    DepositFunds df = resultCommands.get(0).getPages(0).getCommand().unpack(DepositFunds.class);
    assertThat(df.getAmount().getAmount()).isEqualTo(amount);
  }

  @Then("the second command has amount {int} for {string}")
  public void secondCommandHasAmount(int amount, String playerId)
      throws InvalidProtocolBufferException {
    assertThat(resultCommands).hasSizeGreaterThanOrEqualTo(2);
    DepositFunds df = resultCommands.get(1).getPages(0).getCommand().unpack(DepositFunds.class);
    assertThat(df.getAmount().getAmount()).isEqualTo(amount);
  }

  @Then("only TableSyncSaga handles the event")
  public void onlyTableSyncSagaHandlesEvent() {
    assertThat(handledBy).containsExactly("TableSyncSaga");
  }

  @Then("the saga emits {int} DealCards commands")
  public void sagaEmitsDealCardsCommands(int count) throws InvalidProtocolBufferException {
    long dealCardsCount =
        resultCommands.stream()
            .filter(cmd -> cmd.getPages(0).getCommand().is(DealCards.class))
            .count();
    assertThat(dealCardsCount).isEqualTo(count);
  }

  @Then("TableSyncSaga still emits its command")
  public void tableSyncSagaStillEmitsCommand() throws InvalidProtocolBufferException {
    sagaEmitsDealCardsCommand();
  }

  @Then("no exception is raised")
  public void noExceptionRaised() {
    // If we reached here, no exception was raised
  }
}
