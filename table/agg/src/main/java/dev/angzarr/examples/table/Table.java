package dev.angzarr.examples.table;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.util.ByteUtils;
import dev.angzarr.examples.AddChips;
import dev.angzarr.examples.ChipsAdded;
import dev.angzarr.examples.CreateTable;
import dev.angzarr.examples.EndHand;
import dev.angzarr.examples.GameVariant;
import dev.angzarr.examples.HandEnded;
import dev.angzarr.examples.HandStarted;
import dev.angzarr.examples.JoinTable;
import dev.angzarr.examples.LeaveTable;
import dev.angzarr.examples.PlayerJoined;
import dev.angzarr.examples.PlayerLeft;
import dev.angzarr.examples.PlayerSatIn;
import dev.angzarr.examples.PlayerSatOut;
import dev.angzarr.examples.PotResult;
import dev.angzarr.examples.SeatSnapshot;
import dev.angzarr.examples.StartHand;
import dev.angzarr.examples.TableCreated;
import dev.angzarr.examples.table.state.SeatState;
import dev.angzarr.examples.table.state.TableState;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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

  // --- Command handlers ---

  @Handles(CreateTable.class)
  public TableCreated handleCreateTable(CreateTable cmd, TableState state, long seq) {
    if (state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Table already exists");
    }
    if (cmd.getTableName().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("table_name is required");
    }
    if (cmd.getMaxPlayers() < 2 || cmd.getMaxPlayers() > 10) {
      throw Errors.CommandRejectedError.invalidArgument("max_players must be between 2 and 10");
    }
    if (cmd.getSmallBlind() <= 0 || cmd.getBigBlind() <= 0) {
      throw Errors.CommandRejectedError.invalidArgument("blinds must be positive");
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
      throw Errors.CommandRejectedError.preconditionFailed("Table does not exist");
    }
    if (state.getPlayerCount() >= state.getMaxPlayers()) {
      throw Errors.CommandRejectedError.preconditionFailed("Table is full");
    }
    if (state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray()) != null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player already seated at this table");
    }

    long buyIn = cmd.getBuyInAmount();
    if (buyIn < state.getMinBuyIn() || buyIn > state.getMaxBuyIn()) {
      throw Errors.CommandRejectedError.invalidArgument(
          "Buy-in must be at least " + state.getMinBuyIn());
    }

    int seatPosition = cmd.getPreferredSeat();
    if (seatPosition >= 0) {
      if (state.getSeats().containsKey(seatPosition)) {
        throw Errors.CommandRejectedError.preconditionFailed("Seat is occupied");
      }
    } else {
      seatPosition = state.findAvailableSeat();
      if (seatPosition < 0) {
        throw Errors.CommandRejectedError.preconditionFailed("No available seats");
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
      throw Errors.CommandRejectedError.preconditionFailed("Table does not exist");
    }
    SeatState seat = state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray());
    if (seat == null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player is not seated at this table");
    }
    if (state.isInHand()) {
      throw Errors.CommandRejectedError.preconditionFailed("Cannot leave during a hand");
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
      throw Errors.CommandRejectedError.preconditionFailed("Table does not exist");
    }
    if (state.isInHand()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already in progress");
    }
    if (state.getActivePlayerCount() < 2) {
      throw Errors.CommandRejectedError.preconditionFailed("Not enough players to start a hand");
    }

    long handNumber = state.getHandCount() + 1;
    int dealerPosition = state.advanceDealerPosition();
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

    int[] positions = calculateBlindPositions(dealerPosition, activePlayers.size(), state);

    return HandStarted.newBuilder()
        .setHandRoot(ByteString.copyFrom(handRoot))
        .setHandNumber(handNumber)
        .setDealerPosition(dealerPosition)
        .setSmallBlindPosition(positions[0])
        .setBigBlindPosition(positions[1])
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
      throw Errors.CommandRejectedError.preconditionFailed("Table does not exist");
    }
    if (!state.isInHand()) {
      throw Errors.CommandRejectedError.preconditionFailed("No hand in progress");
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

  @Handles(AddChips.class)
  public ChipsAdded handleAddChips(AddChips cmd, TableState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Table does not exist");
    }
    SeatState seat = state.findSeatByPlayer(cmd.getPlayerRoot().toByteArray());
    if (seat == null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player is not seated at this table");
    }
    if (cmd.getAmount() <= 0) {
      throw Errors.CommandRejectedError.invalidArgument("amount must be positive");
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

  private static int[] calculateBlindPositions(
      int dealerPosition, int playerCount, TableState state) {
    if (playerCount == 2) {
      return new int[] {dealerPosition, (dealerPosition + 1) % state.getMaxPlayers()};
    }
    int sb = (dealerPosition + 1) % state.getMaxPlayers();
    int bb = (dealerPosition + 2) % state.getMaxPlayers();
    return new int[] {sb, bb};
  }
}
