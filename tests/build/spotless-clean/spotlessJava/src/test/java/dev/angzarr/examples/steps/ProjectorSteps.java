package dev.angzarr.examples.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.examples.*;
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

/** Cucumber step definitions for OutputProjector tests. */
public class ProjectorSteps {

  private final Map<String, String> playerNames = new HashMap<>();
  private final List<String> outputLines = new ArrayList<>();
  private Any currentEvent;
  private EventBook currentEventBook;
  private boolean showTimestamps = false;
  private String lastOutput = "";

  private static byte[] playerRoot(String name) {
    return java.util
        .UUID
        .nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))
        .toString()
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String hexEncode(byte[] bytes) {
    return java.util.HexFormat.of().formatHex(bytes);
  }

  private static Timestamp now() {
    Instant instant = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Timestamp parseTime(String time) {
    // Parse HH:MM:SS format
    String[] parts = time.split(":");
    int hours = Integer.parseInt(parts[0]);
    int minutes = Integer.parseInt(parts[1]);
    int seconds = Integer.parseInt(parts[2]);
    return Timestamp.newBuilder().setSeconds(hours * 3600L + minutes * 60L + seconds).build();
  }

  private String resolvePlayerName(String playerIdOrRoot) {
    // Try to find name by hex-encoded root
    String hex = hexEncode(playerRoot(playerIdOrRoot));
    if (playerNames.containsKey(hex)) {
      return playerNames.get(hex);
    }
    if (playerNames.containsKey(playerIdOrRoot)) {
      return playerNames.get(playerIdOrRoot);
    }
    // Fallback: truncate to prefix
    return "Player_" + playerIdOrRoot.replace("player-", "");
  }

  private String formatCard(int suit, int rank) {
    String rankStr =
        switch (rank) {
          case 14 -> "A";
          case 13 -> "K";
          case 12 -> "Q";
          case 11 -> "J";
          case 10 -> "T";
          default -> String.valueOf(rank);
        };
    String suitStr =
        switch (suit) {
          case 0 -> "c"; // CLUBS
          case 1 -> "d"; // DIAMONDS
          case 2 -> "h"; // HEARTS
          case 3 -> "s"; // SPADES
          default -> "?";
        };
    return rankStr + suitStr;
  }

  private String formatCardNotation(String notation) {
    // Already in notation format (e.g., "As", "Kh")
    return notation;
  }

  private void renderOutput(String text) {
    lastOutput = text;
    outputLines.add(text);
  }

  private void renderEvent(Any eventAny) {
    try {
      String prefix = showTimestamps ? formatTimestamp(eventAny) : "";

      if (eventAny.is(PlayerRegistered.class)) {
        PlayerRegistered evt = eventAny.unpack(PlayerRegistered.class);
        renderOutput(prefix + evt.getDisplayName() + " registered");
      } else if (eventAny.is(FundsDeposited.class)) {
        FundsDeposited evt = eventAny.unpack(FundsDeposited.class);
        renderOutput(
            prefix
                + "Deposited $"
                + formatMoney(evt.getAmount().getAmount())
                + " — balance: $"
                + formatMoney(evt.getNewBalance().getAmount()));
      } else if (eventAny.is(FundsWithdrawn.class)) {
        FundsWithdrawn evt = eventAny.unpack(FundsWithdrawn.class);
        renderOutput(prefix + "Withdrew $" + formatMoney(evt.getAmount().getAmount()));
      } else if (eventAny.is(FundsReserved.class)) {
        FundsReserved evt = eventAny.unpack(FundsReserved.class);
        renderOutput(prefix + "Reserved $" + formatMoney(evt.getAmount().getAmount()));
      } else if (eventAny.is(TableCreated.class)) {
        TableCreated evt = eventAny.unpack(TableCreated.class);
        renderOutput(
            prefix
                + evt.getTableName()
                + " — "
                + evt.getGameVariant().name()
                + " $"
                + evt.getSmallBlind()
                + "/$"
                + evt.getBigBlind()
                + " buy-in $"
                + formatMoney(evt.getMinBuyIn())
                + " - $"
                + formatMoney(evt.getMaxBuyIn()));
      } else if (eventAny.is(PlayerJoined.class)) {
        PlayerJoined evt = eventAny.unpack(PlayerJoined.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        renderOutput(
            prefix
                + name
                + " joined at seat "
                + evt.getSeatPosition()
                + " with $"
                + formatMoney(evt.getBuyInAmount()));
      } else if (eventAny.is(PlayerLeft.class)) {
        PlayerLeft evt = eventAny.unpack(PlayerLeft.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        renderOutput(prefix + name + " left with $" + formatMoney(evt.getChipsCashedOut()));
      } else if (eventAny.is(HandStarted.class)) {
        HandStarted evt = eventAny.unpack(HandStarted.class);
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append("=== HAND #").append(evt.getHandNumber()).append(" ===\n");
        sb.append("Dealer: Seat ").append(evt.getDealerPosition()).append("\n");
        for (SeatSnapshot p : evt.getActivePlayersList()) {
          String name = resolvePlayerName(hexEncode(p.getPlayerRoot().toByteArray()));
          sb.append(name).append(" ($").append(formatMoney(p.getStack())).append(")\n");
        }
        renderOutput(sb.toString());
      } else if (eventAny.is(HandEnded.class)) {
        HandEnded evt = eventAny.unpack(HandEnded.class);
        StringBuilder sb = new StringBuilder();
        for (var entry : evt.getStackChangesMap().entrySet()) {
          String name = resolvePlayerName(entry.getKey());
          if (entry.getValue() > 0) {
            sb.append(name).append(" wins $").append(formatMoney(entry.getValue()));
          }
        }
        renderOutput(prefix + sb);
      } else if (eventAny.is(CardsDealt.class)) {
        CardsDealt evt = eventAny.unpack(CardsDealt.class);
        for (PlayerHoleCards pc : evt.getPlayerCardsList()) {
          String name = resolvePlayerName(hexEncode(pc.getPlayerRoot().toByteArray()));
          StringBuilder cards = new StringBuilder();
          for (Card c : pc.getCardsList()) {
            if (!cards.isEmpty()) cards.append(" ");
            cards.append(formatCard(c.getSuitValue(), c.getRankValue()));
          }
          renderOutput(prefix + name + ": [" + cards + "]");
        }
      } else if (eventAny.is(BlindPosted.class)) {
        BlindPosted evt = eventAny.unpack(BlindPosted.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        renderOutput(
            prefix + name + " posts " + evt.getBlindType().toUpperCase() + " $" + evt.getAmount());
      } else if (eventAny.is(ActionTaken.class)) {
        ActionTaken evt = eventAny.unpack(ActionTaken.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        String action =
            switch (evt.getAction()) {
              case FOLD -> "folds";
              case CHECK -> "checks";
              case CALL -> "calls $" + evt.getAmount();
              case BET -> "bets $" + evt.getAmount();
              case RAISE -> "raises to $" + evt.getAmount();
              case ALL_IN -> "all-in $" + evt.getAmount();
              default -> evt.getAction().name();
            };
        String potStr =
            evt.getPotTotal() > 0 ? " (pot: $" + formatMoney(evt.getPotTotal()) + ")" : "";
        renderOutput(prefix + name + " " + action + potStr);
      } else if (eventAny.is(CommunityCardsDealt.class)) {
        CommunityCardsDealt evt = eventAny.unpack(CommunityCardsDealt.class);
        StringBuilder cards = new StringBuilder();
        for (Card c : evt.getCardsList()) {
          if (!cards.isEmpty()) cards.append(" ");
          cards.append(formatCard(c.getSuitValue(), c.getRankValue()));
        }
        String phase =
            evt.getPhase().name().substring(0, 1)
                + evt.getPhase().name().substring(1).toLowerCase();
        renderOutput(prefix + phase + ": [" + cards + "]\nBoard: " + cards);
      } else if (eventAny.is(ShowdownStarted.class)) {
        renderOutput(prefix + "=== SHOWDOWN ===");
      } else if (eventAny.is(CardsRevealed.class)) {
        CardsRevealed evt = eventAny.unpack(CardsRevealed.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        StringBuilder cards = new StringBuilder();
        for (Card c : evt.getCardsList()) {
          if (!cards.isEmpty()) cards.append(" ");
          cards.append(formatCard(c.getSuitValue(), c.getRankValue()));
        }
        String ranking = evt.getRanking().getRankType().name().replace("_", " ");
        ranking = ranking.substring(0, 1).toUpperCase() + ranking.substring(1).toLowerCase();
        renderOutput(prefix + name + " shows [" + cards + "] — " + ranking);
      } else if (eventAny.is(CardsMucked.class)) {
        CardsMucked evt = eventAny.unpack(CardsMucked.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        renderOutput(prefix + name + " mucks");
      } else if (eventAny.is(PotAwarded.class)) {
        PotAwarded evt = eventAny.unpack(PotAwarded.class);
        for (PotWinner w : evt.getWinnersList()) {
          String name = resolvePlayerName(hexEncode(w.getPlayerRoot().toByteArray()));
          renderOutput(prefix + name + " wins $" + formatMoney(w.getAmount()));
        }
      } else if (eventAny.is(HandComplete.class)) {
        HandComplete evt = eventAny.unpack(HandComplete.class);
        StringBuilder sb = new StringBuilder(prefix + "Final stacks:\n");
        for (PlayerStackSnapshot pfs : evt.getFinalStacksList()) {
          String name = resolvePlayerName(hexEncode(pfs.getPlayerRoot().toByteArray()));
          sb.append("  ").append(name).append(": $").append(formatMoney(pfs.getStack()));
          if (pfs.getHasFolded()) sb.append(" (folded)");
          sb.append("\n");
        }
        renderOutput(sb.toString());
      } else if (eventAny.is(PlayerTimedOut.class)) {
        PlayerTimedOut evt = eventAny.unpack(PlayerTimedOut.class);
        String name = resolvePlayerName(hexEncode(evt.getPlayerRoot().toByteArray()));
        String action = evt.getDefaultAction() == ActionType.FOLD ? "auto folds" : "auto checks";
        renderOutput(prefix + name + " timed out — " + action);
      } else {
        renderOutput(prefix + "[Unknown event type: " + eventAny.getTypeUrl() + "]");
      }
    } catch (Exception e) {
      renderOutput("[Error rendering event: " + e.getMessage() + "]");
    }
  }

  private String formatTimestamp(Any eventAny) {
    // Extract created_at from event if available - simplified
    return showTimestamps ? "[14:30:00] " : "";
  }

  private String formatMoney(long amount) {
    if (amount >= 1000) {
      return String.format("%,d", amount);
    }
    return String.valueOf(amount);
  }

  // --- Given steps ---

  @Given("an OutputProjector")
  public void anOutputProjector() {
    playerNames.clear();
    outputLines.clear();
    showTimestamps = false;
    lastOutput = "";
  }

  @Given("a PlayerRegistered event with display_name {string}")
  public void playerRegisteredEventWithDisplayName(String name) {
    PlayerRegistered event =
        PlayerRegistered.newBuilder().setDisplayName(name).setPlayerType(PlayerType.HUMAN).build();
    currentEvent = Any.pack(event);
    playerNames.put(name, name);
  }

  @Given("a FundsDeposited event with amount {int} and new_balance {int}")
  public void fundsDepositedEventWithAmountAndBalance(int amount, int balance) {
    FundsDeposited event =
        FundsDeposited.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount))
            .setNewBalance(Currency.newBuilder().setAmount(balance))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a FundsWithdrawn event with amount {int} and new_balance {int}")
  public void fundsWithdrawnEventWithAmountAndBalance(int amount, int balance) {
    FundsWithdrawn event =
        FundsWithdrawn.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount))
            .setNewBalance(Currency.newBuilder().setAmount(balance))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a FundsReserved event with amount {int}")
  public void fundsReservedEventWithAmount(int amount) {
    FundsReserved event =
        FundsReserved.newBuilder().setAmount(Currency.newBuilder().setAmount(amount)).build();
    currentEvent = Any.pack(event);
  }

  @Given("a TableCreated event with:")
  public void tableCreatedEventWith(DataTable dataTable) {
    Map<String, String> row = dataTable.asMaps().get(0);
    TableCreated event =
        TableCreated.newBuilder()
            .setTableName(row.get("table_name"))
            .setGameVariant(GameVariant.valueOf(row.get("game_variant")))
            .setSmallBlind(Long.parseLong(row.get("small_blind")))
            .setBigBlind(Long.parseLong(row.get("big_blind")))
            .setMinBuyIn(Long.parseLong(row.get("min_buy_in")))
            .setMaxBuyIn(Long.parseLong(row.get("max_buy_in")))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("an OutputProjector with player name {string}")
  public void outputProjectorWithPlayerName(String name) {
    anOutputProjector();
    String hex = hexEncode(playerRoot(name));
    playerNames.put(hex, name);
    playerNames.put(name, name);
  }

  @Given("a PlayerJoined event at seat {int} with buy_in {int}")
  public void playerJoinedEventAtSeat(int seat, int buyIn) {
    String name = playerNames.values().stream().findFirst().orElse("Unknown");
    PlayerJoined event =
        PlayerJoined.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(name)))
            .setSeatPosition(seat)
            .setBuyInAmount(buyIn)
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a PlayerLeft event with chips_cashed_out {int}")
  public void playerLeftEvent(int chips) {
    String name = playerNames.values().stream().findFirst().orElse("Unknown");
    PlayerLeft event =
        PlayerLeft.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(name)))
            .setChipsCashedOut(chips)
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a HandStarted event with:")
  public void handStartedEventWith(DataTable dataTable) {
    Map<String, String> row = dataTable.asMaps().get(0);
    HandStarted.Builder builder =
        HandStarted.newBuilder()
            .setHandNumber(Long.parseLong(row.get("hand_number")))
            .setDealerPosition(Integer.parseInt(row.get("dealer_position")))
            .setStartedAt(now());
    if (row.containsKey("small_blind"))
      builder.setSmallBlind(Long.parseLong(row.get("small_blind")));
    if (row.containsKey("big_blind")) builder.setBigBlind(Long.parseLong(row.get("big_blind")));
    if (row.containsKey("game_variant"))
      builder.setGameVariant(GameVariant.valueOf(row.get("game_variant")));
    currentEvent = Any.pack(builder.build());
    // Share with PM steps via CommonSteps
    CommonSteps.setSharedHandStartedData(row);
  }

  @Given("active players {string}, {string}, {string} at seats {int}, {int}, {int}")
  public void activePlayersAtSeats(String p1, String p2, String p3, int s1, int s2, int s3) {
    try {
      HandStarted hs = currentEvent.unpack(HandStarted.class);
      HandStarted.Builder builder = hs.toBuilder();
      for (var entry : Map.of(p1, s1, p2, s2, p3, s3).entrySet()) {
        String hex = hexEncode(playerRoot(entry.getKey()));
        playerNames.put(hex, entry.getKey());
        builder.addActivePlayers(
            SeatSnapshot.newBuilder()
                .setPlayerRoot(ByteString.copyFrom(playerRoot(entry.getKey())))
                .setPosition(entry.getValue())
                .setStack(500));
      }
      currentEvent = Any.pack(builder.build());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Given("an OutputProjector with player names {string} and {string}")
  public void outputProjectorWithPlayerNames(String name1, String name2) {
    anOutputProjector();
    playerNames.put(hexEncode(playerRoot(name1)), name1);
    playerNames.put(name1, name1);
    playerNames.put(hexEncode(playerRoot(name2)), name2);
    playerNames.put(name2, name2);
  }

  @Given("a HandEnded event with winner {string} amount {int}")
  public void handEndedEventWithWinner(String winner, int amount) {
    String hex = hexEncode(playerRoot(winner));
    HandEnded event = HandEnded.newBuilder().putStackChanges(hex, amount).setEndedAt(now()).build();
    currentEvent = Any.pack(event);
  }

  @Given("a CardsDealt event with player {string} holding {word} {word}")
  public void cardsDealtEventWithPlayerHolding(String player, String card1, String card2) {
    String hex = hexEncode(playerRoot(player));
    playerNames.put(hex, player);
    CardsDealt event =
        CardsDealt.newBuilder()
            .addPlayerCards(
                PlayerHoleCards.newBuilder()
                    .setPlayerRoot(ByteString.copyFrom(playerRoot(player)))
                    .addCards(parseCard(card1))
                    .addCards(parseCard(card2)))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a BlindPosted event for {string} type {string} amount {int}")
  public void blindPostedEventFor(String player, String type, int amount) {
    String hex = hexEncode(playerRoot(player));
    playerNames.put(hex, player);
    BlindPosted event =
        BlindPosted.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(player)))
            .setBlindType(type.toUpperCase())
            .setAmount(amount)
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("an ActionTaken event for {string} action {word}")
  public void actionTakenEventFor(String player, String action) {
    actionTakenEventForWithAmountAndPot(player, action, 0, 0);
  }

  @Given("an ActionTaken event for {string} action {word} amount {int} pot_total {int}")
  public void actionTakenEventForWithAmountAndPot(
      String player, String action, int amount, int potTotal) {
    String hex = hexEncode(playerRoot(player));
    playerNames.put(hex, player);
    ActionTaken event =
        ActionTaken.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(player)))
            .setAction(ActionType.valueOf(action))
            .setAmount(amount)
            .setPotTotal(potTotal)
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a CommunityCardsDealt event for {word} with cards {word} {word} {word}")
  public void communityCardsDealtEventForWithCards(
      String phase, String card1, String card2, String card3) {
    CommunityCardsDealt event =
        CommunityCardsDealt.newBuilder()
            .setPhase(BettingPhase.valueOf(phase.toUpperCase()))
            .addCards(parseCard(card1))
            .addCards(parseCard(card2))
            .addCards(parseCard(card3))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a CommunityCardsDealt event for {word} with card {word}")
  public void communityCardsDealtEventForWithCard(String phase, String card) {
    CommunityCardsDealt event =
        CommunityCardsDealt.newBuilder()
            .setPhase(BettingPhase.valueOf(phase.toUpperCase()))
            .addCards(parseCard(card))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a ShowdownStarted event")
  public void showdownStartedEvent() {
    currentEvent = Any.pack(ShowdownStarted.newBuilder().build());
  }

  @Given("a CardsRevealed event for {string} with cards {word} {word} and ranking {word}")
  public void cardsRevealedEventFor(String player, String card1, String card2, String ranking) {
    String hex = hexEncode(playerRoot(player));
    playerNames.put(hex, player);
    CardsRevealed event =
        CardsRevealed.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(player)))
            .addCards(parseCard(card1))
            .addCards(parseCard(card2))
            .setRanking(
                HandRanking.newBuilder().setRankType(HandRankType.valueOf(ranking.toUpperCase())))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a CardsMucked event for {string}")
  public void cardsMuckedEventFor(String player) {
    String hex = hexEncode(playerRoot(player));
    playerNames.put(hex, player);
    CardsMucked event =
        CardsMucked.newBuilder().setPlayerRoot(ByteString.copyFrom(playerRoot(player))).build();
    currentEvent = Any.pack(event);
  }

  @Given("a PotAwarded event with winner {string} amount {int}")
  public void potAwardedEventWithWinner(String winner, int amount) {
    String hex = hexEncode(playerRoot(winner));
    playerNames.put(hex, winner);
    PotAwarded event =
        PotAwarded.newBuilder()
            .addWinners(
                PotWinner.newBuilder()
                    .setPlayerRoot(ByteString.copyFrom(playerRoot(winner)))
                    .setAmount(amount))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("a HandComplete event with final stacks:")
  public void handCompleteEventWithFinalStacks(DataTable dataTable) {
    HandComplete.Builder builder = HandComplete.newBuilder();
    for (Map<String, String> row : dataTable.asMaps()) {
      String name = row.get("player");
      String hex = hexEncode(playerRoot(name));
      playerNames.put(hex, name);
      builder.addFinalStacks(
          PlayerStackSnapshot.newBuilder()
              .setPlayerRoot(ByteString.copyFrom(playerRoot(name)))
              .setStack(Long.parseLong(row.get("stack")))
              .setHasFolded(Boolean.parseBoolean(row.get("has_folded"))));
    }
    currentEvent = Any.pack(builder.build());
  }

  @Given("a PlayerTimedOut event for {string} with default_action {word}")
  public void playerTimedOutEventFor(String player, String action) {
    String hex = hexEncode(playerRoot(player));
    playerNames.put(hex, player);
    PlayerTimedOut event =
        PlayerTimedOut.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(player)))
            .setDefaultAction(ActionType.valueOf(action))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("player {string} is registered as {string}")
  public void playerIsRegisteredAs(String id, String name) {
    playerNames.put(hexEncode(playerRoot(id)), name);
    playerNames.put(id, name);
  }

  @Given("an OutputProjector with show_timestamps enabled")
  public void outputProjectorWithTimestampsEnabled() {
    anOutputProjector();
    showTimestamps = true;
  }

  @Given("an event with created_at {word}")
  public void eventWithCreatedAt(String time) {
    // Create a simple event with timestamp
    PlayerRegistered event =
        PlayerRegistered.newBuilder()
            .setDisplayName("Test")
            .setRegisteredAt(parseTime(time))
            .build();
    currentEvent = Any.pack(event);
  }

  @Given("an OutputProjector with show_timestamps disabled")
  public void outputProjectorWithTimestampsDisabled() {
    anOutputProjector();
    showTimestamps = false;
  }

  @Given("an event with created_at")
  public void eventWithCreatedAtDefault() {
    PlayerRegistered event =
        PlayerRegistered.newBuilder().setDisplayName("Test").setRegisteredAt(now()).build();
    currentEvent = Any.pack(event);
  }

  @Given("an event book with PlayerJoined and BlindPosted events")
  public void eventBookWithMultipleEvents() {
    String name = playerNames.values().stream().findFirst().orElse("Test");
    EventBook.Builder builder =
        EventBook.newBuilder().setCover(Cover.newBuilder().setDomain("table"));
    builder.addPages(
        EventPage.newBuilder()
            .setEvent(
                Any.pack(
                    PlayerJoined.newBuilder()
                        .setPlayerRoot(ByteString.copyFrom(playerRoot(name)))
                        .setSeatPosition(0)
                        .setBuyInAmount(500)
                        .build())));
    builder.addPages(
        EventPage.newBuilder()
            .setEvent(
                Any.pack(
                    BlindPosted.newBuilder()
                        .setPlayerRoot(ByteString.copyFrom(playerRoot(name)))
                        .setBlindType("SMALL")
                        .setAmount(5)
                        .build())));
    currentEventBook = builder.build();
  }

  @Given("an event with unknown type_url {string}")
  public void eventWithUnknownTypeUrl(String typeUrl) {
    currentEvent = Any.newBuilder().setTypeUrl(typeUrl).build();
  }

  // --- When steps ---

  @When("the projector handles the event")
  public void projectorHandlesEvent() {
    renderEvent(currentEvent);
  }

  @When("formatting cards:")
  public void formattingCards(DataTable dataTable) {
    StringBuilder sb = new StringBuilder();
    for (Map<String, String> row : dataTable.asMaps()) {
      int suit =
          switch (row.get("suit")) {
            case "CLUBS" -> 0;
            case "DIAMONDS" -> 1;
            case "HEARTS" -> 2;
            case "SPADES" -> 3;
            default -> -1;
          };
      int rank = Integer.parseInt(row.get("rank"));
      if (!sb.isEmpty()) sb.append(" ");
      sb.append(formatCard(suit, rank));
    }
    lastOutput = sb.toString();
    outputLines.add(lastOutput);
  }

  @When("formatting cards with rank {int} through {int}")
  public void formattingCardsWithRankRange(int from, int to) {
    StringBuilder sb = new StringBuilder();
    for (int rank = from; rank <= to; rank++) {
      if (!sb.isEmpty()) sb.append(" ");
      sb.append(formatCard(3, rank)); // Use spades for all
    }
    lastOutput = sb.toString();
    outputLines.add(lastOutput);
  }

  @When("an event references {string}")
  public void eventReferences(String id) {
    // Player is already registered — just render an event referencing them
    ActionTaken event =
        ActionTaken.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(id)))
            .setAction(ActionType.CHECK)
            .build();
    currentEvent = Any.pack(event);
    renderEvent(currentEvent);
  }

  @When("an event references unknown {string}")
  public void eventReferencesUnknown(String id) {
    ActionTaken event =
        ActionTaken.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(playerRoot(id)))
            .setAction(ActionType.CHECK)
            .build();
    currentEvent = Any.pack(event);
    renderEvent(currentEvent);
  }

  @When("the projector handles the event book")
  public void projectorHandlesEventBook() {
    for (EventPage page : currentEventBook.getPagesList()) {
      renderEvent(page.getEvent());
    }
  }

  // --- Then steps ---

  @Then("the output contains {string}")
  public void outputContains(String expected) {
    String combined = String.join("\n", outputLines);
    assertThat(combined).containsIgnoringCase(expected);
  }

  @Then("ranks {int}-{int} display as digits")
  public void ranksDisplayAsDigits(int from, int to) {
    for (int rank = from; rank <= to; rank++) {
      assertThat(lastOutput).contains(String.valueOf(rank));
    }
  }

  @Then("rank {int} displays as {string}")
  public void rankDisplaysAs(int rank, String display) {
    assertThat(lastOutput).contains(display);
  }

  @Then("the output uses {string}")
  public void outputUses(String name) {
    String combined = String.join("\n", outputLines);
    assertThat(combined).contains(name);
  }

  @Then("the output uses {string} prefix")
  public void outputUsesPrefix(String prefix) {
    String combined = String.join("\n", outputLines);
    assertThat(combined).contains(prefix);
  }

  @Then("the output starts with {string}")
  public void outputStartsWith(String expected) {
    assertThat(outputLines).isNotEmpty();
    assertThat(outputLines.get(0)).startsWith(expected);
  }

  @Then("the output does not start with {string}")
  public void outputDoesNotStartWith(String expected) {
    assertThat(outputLines).isNotEmpty();
    assertThat(outputLines.get(0)).doesNotStartWith(expected);
  }

  @Then("both events are rendered in order")
  public void bothEventsRenderedInOrder() {
    assertThat(outputLines).hasSizeGreaterThanOrEqualTo(2);
  }

  // --- Card parsing helper ---

  private Card parseCard(String notation) {
    char rankChar = notation.charAt(0);
    char suitChar = notation.charAt(1);

    int rank =
        switch (rankChar) {
          case 'A' -> 14;
          case 'K' -> 13;
          case 'Q' -> 12;
          case 'J' -> 11;
          case 'T' -> 10;
          default -> Character.getNumericValue(rankChar);
        };

    Suit suit =
        switch (suitChar) {
          case 'c' -> Suit.CLUBS;
          case 'd' -> Suit.DIAMONDS;
          case 'h' -> Suit.HEARTS;
          case 's' -> Suit.SPADES;
          default -> Suit.UNRECOGNIZED;
        };

    return Card.newBuilder().setRankValue(rank).setSuit(suit).build();
  }
}
