package dev.angzarr.examples.table;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.util.ByteUtils;
import dev.angzarr.examples.AddChips;
import dev.angzarr.examples.AddRebuyChips;
import dev.angzarr.examples.BlindDodgePenalty;
import dev.angzarr.examples.ChangeSeats;
import dev.angzarr.examples.ChipsAdded;
import dev.angzarr.examples.CreateTable;
import dev.angzarr.examples.EndHand;
import dev.angzarr.examples.EndTableHandForHand;
import dev.angzarr.examples.EnterTableHandForHand;
import dev.angzarr.examples.GameVariant;
import dev.angzarr.examples.HandEnded;
import dev.angzarr.examples.HandStarted;
import dev.angzarr.examples.JoinTable;
import dev.angzarr.examples.LeaveTable;
import dev.angzarr.examples.MarkTableHandForHandHandComplete;
import dev.angzarr.examples.PlayerJoined;
import dev.angzarr.examples.PlayerLeft;
import dev.angzarr.examples.PlayerSatIn;
import dev.angzarr.examples.PlayerSatOut;
import dev.angzarr.examples.PlayerSeated;
import dev.angzarr.examples.PotResult;
import dev.angzarr.examples.RebuyChipsAdded;
import dev.angzarr.examples.SeatPlayer;
import dev.angzarr.examples.SeatSnapshot;
import dev.angzarr.examples.SeatingRejected;
import dev.angzarr.examples.StartHand;
import dev.angzarr.examples.TableCreated;
import dev.angzarr.examples.TableHandForHandEnded;
import dev.angzarr.examples.TableHandForHandRoundComplete;
import dev.angzarr.examples.TableHandForHandWaiting;
import dev.angzarr.examples.table.errors.TableErrors;
import dev.angzarr.examples.table.state.SeatState;
import dev.angzarr.examples.table.state.TableState;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Table aggregate — Tier 5 annotation-driven. Manages game session, seating, and hand lifecycle.
 */
@Aggregate(domain = "table", state = TableState.class)
public class Table {

  public static final String DOMAIN = "table";

  // --- Event appliers ---

  @Applies(TableCreated.class)
  public void applyTableCreated(TableState state, TableCreated event) {
    state.setTableId("table_" + event.getTableName());
    state.setTableName(event.getTableName());
    state.setGameVariant(event.getGameVariantValue());
    state.setSmallBlind(event.getSmallBlind());
    state.setBigBlind(event.getBigBlind());
    state.setMinBuyIn(event.getMinBuyIn());
    state.setMaxBuyIn(event.getMaxBuyIn());
    state.setMaxPlayers(event.getMaxPlayers());
    state.setActionTimeoutSeconds(event.getActionTimeoutSeconds());
    state.setStatus("waiting");
    state.setDealerPosition(0);
    state.setHandCount(0);
  }

  @Applies(PlayerJoined.class)
  public void applyPlayerJoined(TableState state, PlayerJoined event) {
    SeatState seat = new SeatState(event.getSeatPosition());
    seat.setPlayerRoot(event.getPlayerRoot().toByteArray());
    seat.setStack(event.getStack());
    seat.setActive(true);
    seat.setSittingOut(false);
    state.getSeats().put(event.getSeatPosition(), seat);
  }

  @Applies(PlayerLeft.class)
  public void applyPlayerLeft(TableState state, PlayerLeft event) {
    state.getSeats().remove(event.getSeatPosition());
  }

  @Applies(PlayerSatOut.class)
  public void applyPlayerSatOut(TableState state, PlayerSatOut event) {
    SeatState seat = state.findSeatByPlayer(event.getPlayerRoot().toByteArray());
    if (seat != null) {
      seat.setSittingOut(true);
    }
  }

  @Applies(PlayerSatIn.class)
  public void applyPlayerSatIn(TableState state, PlayerSatIn event) {
    SeatState seat = state.findSeatByPlayer(event.getPlayerRoot().toByteArray());
    if (seat != null) {
      seat.setSittingOut(false);
    }
  }

  @Applies(HandStarted.class)
  public void applyHandStarted(TableState state, HandStarted event) {
    state.setStatus("in_hand");
    state.setCurrentHandRoot(event.getHandRoot().toByteArray());
    state.setHandCount(event.getHandNumber());
    state.setDealerPosition(event.getDealerPosition());
    // TDA Rule 35 — persist this hand's blind positions so the NEXT hand's
    // start-hand handler can detect bust-between-hands (BB seat empty →
    // dead-button freeze) per Py canonical apply_hand_started lines 216-217.
    state.setLastSmallBlindPosition(event.getSmallBlindPosition());
    state.setLastBigBlindPosition(event.getBigBlindPosition());
  }

  @Applies(HandEnded.class)
  public void applyHandEnded(TableState state, HandEnded event) {
    state.setStatus("waiting");
    state.setCurrentHandRoot(new byte[0]);
    for (Map.Entry<String, Long> entry : event.getStackChangesMap().entrySet()) {
      String playerHex = entry.getKey();
      long delta = entry.getValue();
      for (SeatState seat : state.getSeats().values()) {
        if (ByteUtils.bytesToHex(seat.getPlayerRoot()).equals(playerHex)) {
          seat.setStack(seat.getStack() + delta);
        }
      }
    }
  }

  @Applies(ChipsAdded.class)
  public void applyChipsAdded(TableState state, ChipsAdded event) {
    SeatState seat = state.findSeatByPlayer(event.getPlayerRoot().toByteArray());
    if (seat != null) {
      seat.setStack(event.getNewStack());
    }
  }

  // Phase I-Java HIGH-EX-2.2.1 — SeatPlayer applier (buy-in confirmation path).
  @Applies(PlayerSeated.class)
  public void applyPlayerSeated(TableState state, PlayerSeated event) {
    if (state.getSeats().containsKey(event.getSeatPosition())) {
      SeatState existing = state.getSeats().get(event.getSeatPosition());
      existing.setPlayerRoot(event.getPlayerRoot().toByteArray());
      existing.setStack(event.getStack());
      existing.setActive(true);
      existing.setSittingOut(false);
    } else {
      SeatState seat = new SeatState(event.getSeatPosition());
      seat.setPlayerRoot(event.getPlayerRoot().toByteArray());
      seat.setStack(event.getStack());
      seat.setActive(true);
      seat.setSittingOut(false);
      state.getSeats().put(event.getSeatPosition(), seat);
    }
  }

  // SeatingRejected carries no state change — the seat assignment was the rejection itself.
  // Mirrors examples-python/main/table/agg/handlers/table.py::apply_seating_rejected (no-op).
  @Applies(SeatingRejected.class)
  public void applySeatingRejected(TableState state, SeatingRejected event) {
    // no-op
  }

  // Phase I-Java HIGH-EX-2.2.2 — RebuyChipsAdded applier (tournament rebuy path).
  @Applies(RebuyChipsAdded.class)
  public void applyRebuyChipsAdded(TableState state, RebuyChipsAdded event) {
    SeatState seat = state.findSeatByPlayer(event.getPlayerRoot().toByteArray());
    if (seat != null) {
      seat.setStack(event.getNewStack());
    }
  }

  // Phase I-Java HIGH-EX-2.2.3 — hand-for-hand appliers.

  @Applies(TableHandForHandWaiting.class)
  public void applyTableHandForHandWaiting(TableState state, TableHandForHandWaiting event) {
    state.setHandForHandStatus("WAITING");
    state.setHandForHandTournamentRoot(event.getTournamentRoot().toByteArray());
  }

  @Applies(TableHandForHandRoundComplete.class)
  public void applyTableHandForHandRoundComplete(
      TableState state, TableHandForHandRoundComplete event) {
    state.setHandForHandStatus("COMPLETE");
  }

  @Applies(TableHandForHandEnded.class)
  public void applyTableHandForHandEnded(TableState state, TableHandForHandEnded event) {
    state.setHandForHandStatus("NONE");
    state.setHandForHandTournamentRoot(new byte[0]);
  }

  // --- Command handlers ---

  @Handles(CreateTable.class)
  public TableCreated handleCreateTable(CreateTable cmd, TableState state, long seq) {
    if (state.exists()) {
      throw TableErrors.tableAlreadyExists();
    }
    if (cmd.getTableName().isEmpty()) {
      throw TableErrors.tableNameRequired();
    }
    // Py order: small_blind > 0 → big_blind >= small_blind → min_buy_in > 0
    // → max_buy_in >= min_buy_in → max_players in [2,10].
    if (cmd.getSmallBlind() <= 0) {
      throw TableErrors.smallBlindMustBePositive(cmd.getSmallBlind());
    }
    if (cmd.getBigBlind() < cmd.getSmallBlind()) {
      throw TableErrors.bigBlindMustExceedSmallBlind(cmd.getBigBlind(), cmd.getSmallBlind());
    }
    if (cmd.getMinBuyIn() <= 0) {
      throw TableErrors.minBuyInMustBePositive(cmd.getMinBuyIn());
    }
    if (cmd.getMaxBuyIn() < cmd.getMinBuyIn()) {
      throw TableErrors.maxBuyInMustExceedMinBuyIn(cmd.getMaxBuyIn(), cmd.getMinBuyIn());
    }
    if (cmd.getMaxPlayers() < 2 || cmd.getMaxPlayers() > 10) {
      throw TableErrors.maxPlayersOutOfRange(cmd.getMaxPlayers());
    }

    return TableCreated.newBuilder()
        .setTableName(cmd.getTableName())
        .setGameVariant(cmd.getGameVariant())
        .setSmallBlind(cmd.getSmallBlind())
        .setBigBlind(cmd.getBigBlind())
        .setMinBuyIn(cmd.getMinBuyIn())
        .setMaxBuyIn(cmd.getMaxBuyIn())
        .setMaxPlayers(cmd.getMaxPlayers())
        .setActionTimeoutSeconds(cmd.getActionTimeoutSeconds())
        .setCreatedAt(now())
        .build();
  }

  @Handles(JoinTable.class)
  public PlayerJoined handleJoinTable(JoinTable cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    if (cmd.getPlayerRoot().isEmpty()) {
      throw TableErrors.playerRootRequired();
    }
    if (state.getPlayerCount() >= state.getMaxPlayers()) {
      throw TableErrors.tableIsFull();
    }
    if (state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray()) != null) {
      throw TableErrors.playerAlreadySeated();
    }

    long buyIn = cmd.getBuyInAmount();
    if (buyIn < state.getMinBuyIn()) {
      throw TableErrors.buyInBelowMin(buyIn, state.getMinBuyIn());
    }
    if (buyIn > state.getMaxBuyIn()) {
      throw TableErrors.buyInAboveMax(buyIn, state.getMaxBuyIn());
    }

    int seatPosition = cmd.getPreferredSeat();
    if (seatPosition >= 0) {
      if (state.getSeats().containsKey(seatPosition)) {
        throw TableErrors.seatOccupied(seatPosition);
      }
    } else {
      seatPosition = state.findAvailableSeat();
      if (seatPosition < 0) {
        throw TableErrors.tableIsFull();
      }
    }

    return PlayerJoined.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setSeatPosition(seatPosition)
        .setBuyInAmount(buyIn)
        .setStack(buyIn)
        .setJoinedAt(now())
        .build();
  }

  @Handles(LeaveTable.class)
  public PlayerLeft handleLeaveTable(LeaveTable cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    if (cmd.getPlayerRoot().isEmpty()) {
      throw TableErrors.playerRootRequired();
    }
    SeatState seat = state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray());
    if (seat == null) {
      throw TableErrors.playerNotSeated();
    }
    if (state.isInHand()) {
      throw TableErrors.cannotLeaveDuringHand();
    }

    return PlayerLeft.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setSeatPosition(seat.getPosition())
        .setChipsCashedOut(seat.getStack())
        .setLeftAt(now())
        .build();
  }

  @Handles(StartHand.class)
  public HandStarted handleStartHand(StartHand cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    if (state.isInHand()) {
      throw TableErrors.handAlreadyInProgress();
    }
    if (state.getActivePlayerCount() < 2) {
      throw TableErrors.notEnoughPlayersToStartHand(2, state.getActivePlayerCount());
    }

    long handNumber = state.getHandCount() + 1;
    byte[] handRoot = generateHandRoot(state.getTableId(), handNumber);

    List<SeatSnapshot> activePlayers = new ArrayList<>();
    for (SeatState seat : state.getSeats().values()) {
      if (seat.isActive()) {
        activePlayers.add(
            SeatSnapshot.newBuilder()
                .setPosition(seat.getPosition())
                .setPlayerRoot(ByteString.copyFrom(seat.getPlayerRoot()))
                .setStack(seat.getStack())
                .build());
      }
    }

    // TDA Rule 35 — dead-button-aware blind advancement. Replaces the
    // naive (dealer+1)%maxPlayers arithmetic so BB busts, SB busts and
    // heads-up collapse all advance per the WSOP / TDA canon.
    int[] adv = advanceBlindsWithDeadButton(state);
    int dealerPosition = adv[0];
    int sbPosition = adv[1];
    int bbPosition = adv[2];

    return HandStarted.newBuilder()
        .setHandRoot(ByteString.copyFrom(handRoot))
        .setHandNumber(handNumber)
        .setDealerPosition(dealerPosition)
        .setSmallBlindPosition(sbPosition)
        .setBigBlindPosition(bbPosition)
        .addAllActivePlayers(activePlayers)
        .setGameVariant(GameVariant.forNumber(state.getGameVariant()))
        .setSmallBlind(state.getSmallBlind())
        .setBigBlind(state.getBigBlind())
        .setStartedAt(now())
        .build();
  }

  @Handles(EndHand.class)
  public HandEnded handleEndHand(EndHand cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    if (!state.isInHand()) {
      throw TableErrors.noHandInProgress();
    }
    // Py canonical: hand_root must match the currently-active hand (table.py::handle_end_hand).
    if (state.getCurrentHandRoot() != null
        && state.getCurrentHandRoot().length > 0
        && !java.util.Arrays.equals(state.getCurrentHandRoot(), cmd.getHandRoot().toByteArray())) {
      throw TableErrors.handRootMismatch();
    }

    Map<String, Long> stackChanges = new HashMap<>();
    for (PotResult result : cmd.getResultsList()) {
      String playerHex = ByteUtils.bytesToHex(result.getWinnerRoot().toByteArray());
      stackChanges.merge(playerHex, result.getAmount(), Long::sum);
    }

    return HandEnded.newBuilder()
        .setHandRoot(cmd.getHandRoot())
        .addAllResults(cmd.getResultsList())
        .putAllStackChanges(stackChanges)
        .setEndedAt(now())
        .build();
  }

  // Phase I-Java HIGH-EX-2.2.1 — SeatPlayer command handler (buy-in path).
  // Py canonical (examples-python/main/table/agg/handlers/table.py::handle_seat_player) NEVER
  // raises for input/state issues — it emits a SeatingRejected event instead so the orchestrating
  // PM can compensate via ReleaseBuyIn. Only "Table does not exist" raises (aggregate-not-found
  // is a routing concern, not a business rejection).
  @Handles(SeatPlayer.class)
  public com.google.protobuf.Message handleSeatPlayer(SeatPlayer cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Table does not exist");
    }
    String reason = null;
    int seatPosition = cmd.getSeat();
    if (cmd.getPlayerRoot().isEmpty()) {
      reason = "player_root is required";
    } else if (state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray()) != null) {
      reason = "Player already seated";
    } else if (cmd.getAmount() < state.getMinBuyIn()) {
      reason = "Buy-in must be at least " + state.getMinBuyIn();
    } else if (cmd.getAmount() > state.getMaxBuyIn()) {
      reason = "Buy-in above maximum";
    } else if (seatPosition >= 0 && seatPosition < state.getMaxPlayers()) {
      if (state.getSeats().containsKey(seatPosition)
          && state.getSeats().get(seatPosition).getPlayerRoot() != null
          && state.getSeats().get(seatPosition).getPlayerRoot().length > 0) {
        reason = "Seat is occupied";
      }
    } else if (seatPosition == -1) {
      int found = state.findAvailableSeat();
      if (found < 0) {
        reason = "Table is full";
      } else {
        seatPosition = found;
      }
    } else {
      reason = "Invalid seat position";
    }

    if (reason == null) {
      return PlayerSeated.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setSeatPosition(seatPosition)
          .setStack(cmd.getAmount())
          .setSeatedAt(now())
          .build();
    }
    return SeatingRejected.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setReservationId(cmd.getReservationId())
        .setRequestedSeat(cmd.getSeat())
        .setReason(reason)
        .setRejectedAt(now())
        .build();
  }

  // Phase I-Java HIGH-EX-2.2.2 — AddRebuyChips command handler (tournament rebuy path).
  @Handles(AddRebuyChips.class)
  public RebuyChipsAdded handleAddRebuyChips(AddRebuyChips cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    if (cmd.getPlayerRoot().isEmpty()) {
      throw TableErrors.playerRootRequired();
    }
    SeatState seat = state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray());
    if (seat == null) {
      throw TableErrors.playerNotSeated();
    }
    if (cmd.getSeat() != seat.getPosition()) {
      throw TableErrors.seatPositionMismatch(seat.getPosition(), cmd.getSeat());
    }
    if (cmd.getAmount() <= 0) {
      throw TableErrors.amountMustBePositive(cmd.getAmount());
    }
    return RebuyChipsAdded.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setReservationId(cmd.getReservationId())
        .setSeat(cmd.getSeat())
        .setAmount(cmd.getAmount())
        .setNewStack(seat.getStack() + cmd.getAmount())
        .setAddedAt(now())
        .build();
  }

  // Phase I-Java HIGH-EX-2.2.3 — three table-side hand-for-hand handlers.

  @Handles(EnterTableHandForHand.class)
  public TableHandForHandWaiting handleEnterTableHandForHand(
      EnterTableHandForHand cmd, TableState state, long seq) {
    return HandForHandHandlers.handleEnterTableHandForHand(cmd, state);
  }

  @Handles(MarkTableHandForHandHandComplete.class)
  public TableHandForHandRoundComplete handleMarkTableHandForHandHandComplete(
      MarkTableHandForHandHandComplete cmd, TableState state, long seq) {
    return HandForHandHandlers.handleMarkTableHandForHandHandComplete(cmd, state);
  }

  @Handles(EndTableHandForHand.class)
  public TableHandForHandEnded handleEndTableHandForHand(
      EndTableHandForHand cmd, TableState state, long seq) {
    return HandForHandHandlers.handleEndTableHandForHand(cmd, state);
  }

  /**
   * PR #12 — ChangeSeats (TDA Rule 33 / WSOP Rule 86). Voluntary between-hand seat change.
   *
   * <p>Design decision #2 — when current_seat == requested_seat, REJECT with structured
   * CommandRejectedError carrying code {@link Codes#SEATS_IDENTICAL}. INVALID_ARGUMENT status
   * (input error). Emitted BEFORE any seat lookup so the early reject is observable.
   *
   * <p>If the move skips a blind position the player forfeits both blinds and the handler emits
   * {@link BlindDodgePenalty}; otherwise the move re-anchors seating via a fresh {@link
   * PlayerSeated} event so the existing applier path mutates state without a new event type.
   */
  @Handles(ChangeSeats.class)
  public com.google.protobuf.Message handleChangeSeats(
      ChangeSeats cmd, TableState state, long seq) {
    // Design decision #2 — SEATS_IDENTICAL fires first so even with a stale
    // table reference the caller learns the request was a no-op.
    if (cmd.getCurrentSeat() == cmd.getRequestedSeat()) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.SEATS_IDENTICAL,
          "current_seat and requested_seat must differ",
          java.util.Map.of(
              "current_seat", String.valueOf(cmd.getCurrentSeat()),
              "requested_seat", String.valueOf(cmd.getRequestedSeat())));
    }
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    if (cmd.getPlayerRoot().isEmpty()) {
      throw TableErrors.playerRootRequired();
    }
    if (state.isInHand()) {
      throw Errors.CommandRejectedError.preconditionFailed(
          "Cannot change seats during an active hand");
    }
    int requested = cmd.getRequestedSeat();
    if (requested < 0 || requested >= state.getMaxPlayers()) {
      throw Errors.CommandRejectedError.invalidArgument(
          "requested_seat out of range [0," + state.getMaxPlayers() + ")");
    }
    SeatState currentSeat = state.getSeat(cmd.getCurrentSeat());
    if (currentSeat == null
        || currentSeat.getPlayerRoot() == null
        || !java.util.Arrays.equals(
            currentSeat.getPlayerRoot(), cmd.getPlayerRoot().toByteArray())) {
      throw Errors.CommandRejectedError.preconditionFailed("Player is not seated at current_seat");
    }
    SeatState target = state.getSeat(requested);
    if (target != null && target.getPlayerRoot() != null && target.getPlayerRoot().length > 0) {
      throw Errors.CommandRejectedError.preconditionFailed("Requested seat is occupied");
    }

    // Blind-dodge detection. If the move skips the last big-blind position
    // (i.e. the player jumps past their blind obligation), the TDA Rule 33
    // penalty fires. We detect "skip" as: prevBb is strictly between the
    // current_seat and requested_seat in CW (modular) order around the
    // table, or, in the simpler linear case, the requested seat is past
    // the BB while the current seat was before it.
    int prevBb = state.getLastBigBlindPosition();
    int maxSeats = state.getMaxPlayers();
    if (prevBb >= 0 && skipsBlind(cmd.getCurrentSeat(), requested, prevBb, maxSeats)) {
      long forfeited = state.getSmallBlind() + state.getBigBlind();
      return BlindDodgePenalty.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setChipsForfeited(forfeited)
          .setMissedRoundCount(1)
          .setAssessedAt(now())
          .build();
    }

    // Re-anchor seating via a fresh PlayerSeated for the new position. The
    // existing PlayerSeated applier mutates state.seats accordingly.
    return PlayerSeated.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setSeatPosition(requested)
        .setStack(currentSeat.getStack())
        .setSeatedAt(now())
        .build();
  }

  /**
   * True iff a CW seat move from {@code from} to {@code to} crosses position {@code bb}. Used to
   * detect TDA Rule 33 blind-dodge. Pure function; package-private for tests.
   *
   * <p>CW traversal starts at {@code from + 1} and increments modulo {@code maxSeats} until it
   * reaches {@code to}. If {@code bb} is encountered along the way (and is not the destination
   * itself), the move skips the blind.
   */
  static boolean skipsBlind(int from, int to, int bb, int maxSeats) {
    if (maxSeats <= 0) return false;
    int cur = (from + 1) % maxSeats;
    while (cur != to) {
      if (cur == bb) {
        return true;
      }
      cur = (cur + 1) % maxSeats;
    }
    return false;
  }

  @Handles(AddChips.class)
  public ChipsAdded handleAddChips(AddChips cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw TableErrors.tableNotFound();
    }
    SeatState seat = state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray());
    if (seat == null) {
      throw TableErrors.playerNotSeated();
    }
    if (cmd.getAmount() <= 0) {
      throw TableErrors.amountMustBePositive(cmd.getAmount());
    }
    if (state.isInHand()) {
      throw Errors.CommandRejectedError.preconditionFailed("Cannot add chips during hand");
    }

    long newStack = seat.getStack() + cmd.getAmount();
    if (newStack > state.getMaxBuyIn()) {
      throw Errors.CommandRejectedError.preconditionFailed("Stack would exceed max buy-in");
    }

    return ChipsAdded.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setAmount(cmd.getAmount())
        .setNewStack(newStack)
        .setAddedAt(now())
        .build();
  }

  // --- Helpers ---

  private static Timestamp now() {
    Instant instant = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static byte[] generateHandRoot(String tableId, long handNumber) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(tableId.getBytes());
      md.update(String.valueOf(handNumber).getBytes());
      byte[] hash = md.digest();
      byte[] result = new byte[16];
      System.arraycopy(hash, 0, result, 0, 16);
      return result;
    } catch (NoSuchAlgorithmException e) {
      return UUID.randomUUID().toString().replace("-", "").substring(0, 32).getBytes();
    }
  }

  /**
   * TDA Rule 35 — compute (dealer, sb, bb) for the next hand using dead-button advancement. Mirrors
   * Py canonical {@code _advance_blinds_with_dead_button} at
   * examples-python/main/table/agg/handlers/table.py lines 441-523.
   *
   * <p>Pure function over {@link TableState}; package-private for tests.
   *
   * <ul>
   *   <li>First hand (prev BB unset, -1): WSOP Rule 85 — dealer starts at the highest-numbered
   *       active seat, blinds derived CW from there.
   *   <li>Heads-up (2 active players): button alternates; new dealer is SB (heads-up structural
   *       rule); other player is BB.
   *   <li>3+ players, BB seat empty (busted between hands): button freezes at prev dealer (dead
   *       button on absent seat); SB = next active seat after prev BB; BB follows SB.
   *   <li>3+ players, standard advance: BB → next active CW from prev BB (no-double-BB invariant);
   *       SB and dealer derived CW-backward through the active set.
   * </ul>
   *
   * @return {@code int[3] = {dealer, sb, bb}}; returns {@code {0, 0, 0}} if fewer than 2 active
   *     seats exist (handled by the caller's pre-flight {@code getActivePlayerCount() < 2} guard).
   */
  static int[] advanceBlindsWithDeadButton(TableState state) {
    List<Integer> active = new ArrayList<>();
    for (SeatState s : state.getSeats().values()) {
      if (s.isActive()) {
        active.add(s.getPosition());
      }
    }
    Collections.sort(active);
    if (active.size() < 2) {
      return new int[] {0, 0, 0};
    }

    int prevBb = state.getLastBigBlindPosition();
    int prevDealer = state.getDealerPosition();

    // First hand: WSOP Rule 85 — start at the highest-numbered active seat.
    if (prevBb < 0) {
      int dealer = active.get(active.size() - 1);
      return deriveBlindPositions(active, dealer);
    }

    // Heads-up: button alternates each hand. New dealer = next active seat
    // CW from prev dealer (or first active if prev dealer busted).
    if (active.size() == 2) {
      int newDealer;
      int prevIdx = active.indexOf(prevDealer);
      if (prevIdx >= 0) {
        newDealer = active.get((prevIdx + 1) % 2);
      } else {
        // Prev dealer busted — pick first active seat past it (wrapping).
        Integer pick = null;
        for (int s : active) {
          if (s > prevDealer) {
            pick = s;
            break;
          }
        }
        newDealer = pick != null ? pick : active.get(0);
      }
      int newSb = newDealer;
      int newBb = active.get(0).equals(newDealer) ? active.get(1) : active.get(0);
      return new int[] {newDealer, newSb, newBb};
    }

    // 3+ players: dead-button special case — prev BB seat vacated.
    boolean prevBbBusted = !active.contains(prevBb);
    if (prevBbBusted) {
      int newDealer = active.contains(prevDealer) ? prevDealer : nextDealerCw(active, prevDealer);
      Integer newSbCandidate = null;
      for (int s : active) {
        if (s > prevBb) {
          newSbCandidate = s;
          break;
        }
      }
      int newSb = newSbCandidate != null ? newSbCandidate : active.get(0);
      int sbIdx = active.indexOf(newSb);
      int newBb = active.get((sbIdx + 1) % active.size());
      return new int[] {newDealer, newSb, newBb};
    }

    // Standard advance: BB → next active CW from prev BB. SB and dealer
    // derived CW-backward from BB through the active set.
    Integer newBbCandidate = null;
    for (int s : active) {
      if (s > prevBb) {
        newBbCandidate = s;
        break;
      }
    }
    int newBb = newBbCandidate != null ? newBbCandidate : active.get(0);
    int bbIdx = active.indexOf(newBb);
    int newSb = active.get((bbIdx - 1 + active.size()) % active.size());
    int sbIdx = active.indexOf(newSb);
    int newDealer = active.get((sbIdx - 1 + active.size()) % active.size());
    return new int[] {newDealer, newSb, newBb};
  }

  private static int nextDealerCw(List<Integer> active, int prevDealer) {
    for (int s : active) {
      if (s > prevDealer) {
        return s;
      }
    }
    return active.get(0);
  }

  private static int[] deriveBlindPositions(List<Integer> active, int dealer) {
    int dIdx = active.indexOf(dealer);
    if (dIdx < 0) {
      dIdx = 0;
    }
    if (active.size() == 2) {
      int sb = active.get(dIdx);
      int bb = active.get((dIdx + 1) % 2);
      return new int[] {dealer, sb, bb};
    }
    int sb = active.get((dIdx + 1) % active.size());
    int bb = active.get((dIdx + 2) % active.size());
    return new int[] {dealer, sb, bb};
  }
}
