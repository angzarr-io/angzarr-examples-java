package dev.angzarr.examples.handflow;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.UUID;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.router.ProcessManagerResponse;
import dev.angzarr.examples.ActionTaken;
import dev.angzarr.examples.ActionType;
import dev.angzarr.examples.AwardPot;
import dev.angzarr.examples.BettingPhase;
import dev.angzarr.examples.BlindPosted;
import dev.angzarr.examples.CardsDealt;
import dev.angzarr.examples.CommunityCardsDealt;
import dev.angzarr.examples.DealCommunityCards;
import dev.angzarr.examples.HandStarted;
import dev.angzarr.examples.PostBlind;
import dev.angzarr.examples.PotAward;
import dev.angzarr.examples.PotAwarded;
import dev.angzarr.examples.SeatSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand Flow Process Manager — Tier 5 annotation-driven.
 *
 * <p>Orchestrates poker hand flow by tracking state and emitting commands to drive hands forward.
 * Uses an in-memory {@code processes} map for demonstration — the Router must be constructed with a
 * singleton factory (see {@link Main}) so this state persists across dispatches.
 *
 * <h3>Sync mode</h3>
 *
 * <p><b>PMs whose next step depends on a command's success/failure must issue those commands in
 * sync mode.</b> This PM is a textbook example: transitioning from {@code POSTING_BLINDS} to {@code
 * BETTING} depends on the {@code PostBlind} command being accepted by the Hand aggregate. Async
 * dispatch would let the PM advance its state before knowing whether the downstream aggregate
 * rejected the command, and the flow would desync. Fire-and-forget commands (e.g. notifications)
 * may stay async.
 */
@ProcessManager(
    name = "hand-flow",
    pmDomain = "hand-flow",
    sources = {"table", "hand"},
    targets = {"hand"},
    state = Struct.class)
public class HandFlowProcessManager {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private final Map<String, HandProcess> processes = new HashMap<>();

  @Handles(HandStarted.class)
  public ProcessManagerResponse onHandStarted(
      HandStarted event, Struct state, Destinations destinations) {
    byte[] handRoot = event.getHandRoot().toByteArray();
    String handId = bytesToHex(handRoot) + "_" + event.getHandNumber();

    HandProcess process = new HandProcess();
    process.handId = handId;
    process.handRoot = handRoot;
    process.handNumber = event.getHandNumber();
    process.dealerPosition = event.getDealerPosition();
    process.smallBlindPosition = event.getSmallBlindPosition();
    process.bigBlindPosition = event.getBigBlindPosition();
    process.smallBlind = event.getSmallBlind();
    process.bigBlind = event.getBigBlind();
    process.phase = HandPhase.DEALING;

    for (SeatSnapshot player : event.getActivePlayersList()) {
      PlayerState ps = new PlayerState();
      ps.playerRoot = player.getPlayerRoot().toByteArray();
      ps.position = player.getPosition();
      ps.stack = player.getStack();
      process.players.put(player.getPosition(), ps);
      process.activePositions.add(player.getPosition());
    }
    Collections.sort(process.activePositions);
    processes.put(handId, process);
    return ProcessManagerResponse.empty(); // DealCards comes from saga
  }

  @Handles(CardsDealt.class)
  public ProcessManagerResponse onCardsDealt(
      CardsDealt event, Struct state, Destinations destinations) {
    String handId = bytesToHex(event.getTableRoot().toByteArray()) + "_" + event.getHandNumber();
    HandProcess process = processes.get(handId);
    if (process == null) return ProcessManagerResponse.empty();

    process.phase = HandPhase.POSTING_BLINDS;
    process.minRaise = process.bigBlind;

    CommandBook cmd = buildPostBlindCommand(process);
    return cmd != null
        ? ProcessManagerResponse.withCommands(List.of(cmd))
        : ProcessManagerResponse.empty();
  }

  @Handles(BlindPosted.class)
  public ProcessManagerResponse onBlindPosted(
      BlindPosted event, Struct state, Destinations destinations) {
    HandProcess process = findProcessByPlayer(event.getPlayerRoot().toByteArray());
    if (process == null) return ProcessManagerResponse.empty();

    for (PlayerState player : process.players.values()) {
      if (Arrays.equals(player.playerRoot, event.getPlayerRoot().toByteArray())) {
        player.stack = event.getPlayerStack();
        player.betThisRound = event.getAmount();
        player.totalInvested = event.getAmount();
        break;
      }
    }

    process.potTotal = event.getPotTotal();

    if ("small".equals(event.getBlindType())) {
      process.smallBlindPosted = true;
      process.currentBet = event.getAmount();
      CommandBook cmd = buildPostBlindCommand(process);
      return cmd != null
          ? ProcessManagerResponse.withCommands(List.of(cmd))
          : ProcessManagerResponse.empty();
    } else if ("big".equals(event.getBlindType())) {
      process.bigBlindPosted = true;
      process.currentBet = event.getAmount();
      startBetting(process);
      return ProcessManagerResponse.empty();
    }
    return ProcessManagerResponse.empty();
  }

  @Handles(ActionTaken.class)
  public ProcessManagerResponse onActionTaken(
      ActionTaken event, Struct state, Destinations destinations) {
    HandProcess process = findProcessByPlayer(event.getPlayerRoot().toByteArray());
    if (process == null) return ProcessManagerResponse.empty();

    for (PlayerState player : process.players.values()) {
      if (Arrays.equals(player.playerRoot, event.getPlayerRoot().toByteArray())) {
        player.stack = event.getPlayerStack();
        player.hasActed = true;
        if (event.getAction() == ActionType.FOLD) {
          player.hasFolded = true;
        } else if (event.getAction() == ActionType.ALL_IN) {
          player.allIn = true;
          player.betThisRound += event.getAmount();
          player.totalInvested += event.getAmount();
        } else if (event.getAction() == ActionType.CALL
            || event.getAction() == ActionType.BET
            || event.getAction() == ActionType.RAISE) {
          player.betThisRound += event.getAmount();
          player.totalInvested += event.getAmount();
        }

        if ((event.getAction() == ActionType.BET
                || event.getAction() == ActionType.RAISE
                || event.getAction() == ActionType.ALL_IN)
            && player.betThisRound > process.currentBet) {
          long raiseAmount = player.betThisRound - process.currentBet;
          process.currentBet = player.betThisRound;
          process.minRaise = Math.max(process.minRaise, raiseAmount);
          process.lastAggressor = player.position;
          for (PlayerState p : process.players.values()) {
            if (p.position != player.position && !p.hasFolded && !p.allIn) {
              p.hasActed = false;
            }
          }
        }
        break;
      }
    }

    process.potTotal = event.getPotTotal();

    if (isBettingComplete(process)) {
      CommandBook cmd = endBettingRound(process);
      return cmd != null
          ? ProcessManagerResponse.withCommands(List.of(cmd))
          : ProcessManagerResponse.empty();
    } else {
      advanceAction(process);
      return ProcessManagerResponse.empty();
    }
  }

  @Handles(CommunityCardsDealt.class)
  public ProcessManagerResponse onCommunityCardsDealt(
      CommunityCardsDealt event, Struct state, Destinations destinations) {
    for (HandProcess process : processes.values()) {
      if (process.phase == HandPhase.DEALING_COMMUNITY) {
        process.communityCardCount = event.getAllCommunityCardsCount();
        process.bettingPhase = event.getPhaseValue();
        startBetting(process);
        return ProcessManagerResponse.empty();
      }
    }
    return ProcessManagerResponse.empty();
  }

  @Handles(PotAwarded.class)
  public ProcessManagerResponse onPotAwarded(
      PotAwarded event, Struct state, Destinations destinations) {
    for (HandProcess process : processes.values()) {
      if (process.phase != HandPhase.COMPLETE) {
        process.phase = HandPhase.COMPLETE;
      }
    }
    return ProcessManagerResponse.empty();
  }

  // --- Helper methods (unchanged from the OO version) ---

  private CommandBook buildPostBlindCommand(HandProcess process) {
    PlayerState player;
    String blindType;
    long amount;

    if (!process.smallBlindPosted) {
      player = process.players.get(process.smallBlindPosition);
      blindType = "small";
      amount = process.smallBlind;
    } else if (!process.bigBlindPosted) {
      player = process.players.get(process.bigBlindPosition);
      blindType = "big";
      amount = process.bigBlind;
    } else {
      return null;
    }
    if (player == null) return null;

    PostBlind cmd =
        PostBlind.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(player.playerRoot))
            .setBlindType(blindType)
            .setAmount(amount)
            .build();

    return CommandBook.newBuilder()
        .setCover(
            Cover.newBuilder()
                .setDomain("hand")
                .setRoot(UUID.newBuilder().setValue(ByteString.copyFrom(process.handRoot))))
        .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd, TYPE_URL_PREFIX)))
        .build();
  }

  private void startBetting(HandProcess process) {
    process.phase = HandPhase.BETTING;
    for (PlayerState player : process.players.values()) {
      player.betThisRound = 0;
      player.hasActed = false;
    }
    process.currentBet = 0;

    if (process.bettingPhase == BettingPhase.PREFLOP_VALUE) {
      process.actionOn = findNextActive(process, process.bigBlindPosition);
    } else {
      process.actionOn = findNextActive(process, process.dealerPosition);
    }
    process.actionStartedAt = Instant.now();
  }

  private void advanceAction(HandProcess process) {
    process.actionOn = findNextActive(process, process.actionOn);
    process.actionStartedAt = Instant.now();
  }

  private int findNextActive(HandProcess process, int afterPosition) {
    List<Integer> positions = process.activePositions;
    int n = positions.size();
    if (n == 0) return -1;

    int startIdx = 0;
    for (int i = 0; i < n; i++) {
      if (positions.get(i) > afterPosition) {
        startIdx = i;
        break;
      }
    }

    for (int i = 0; i < n; i++) {
      int idx = (startIdx + i) % n;
      int pos = positions.get(idx);
      PlayerState player = process.players.get(pos);
      if (player != null && !player.hasFolded && !player.allIn) {
        return pos;
      }
    }
    return -1;
  }

  private boolean isBettingComplete(HandProcess process) {
    List<PlayerState> activePlayers = new ArrayList<>();
    for (PlayerState p : process.players.values()) {
      if (!p.hasFolded && !p.allIn) {
        activePlayers.add(p);
      }
    }
    if (activePlayers.size() <= 1) return true;

    for (PlayerState player : activePlayers) {
      if (!player.hasActed) return false;
      if (player.betThisRound < process.currentBet && !player.allIn) return false;
    }
    return true;
  }

  private CommandBook endBettingRound(HandProcess process) {
    List<PlayerState> playersInHand = new ArrayList<>();
    List<PlayerState> activePlayers = new ArrayList<>();
    for (PlayerState p : process.players.values()) {
      if (!p.hasFolded) {
        playersInHand.add(p);
        if (!p.allIn) activePlayers.add(p);
      }
    }

    if (playersInHand.size() == 1) {
      return awardPotToLastPlayer(process, playersInHand.get(0));
    }
    return advancePhase(process);
  }

  private CommandBook advancePhase(HandProcess process) {
    if (process.bettingPhase == BettingPhase.PREFLOP_VALUE) {
      process.phase = HandPhase.DEALING_COMMUNITY;
      return buildDealCommunityCommand(process, 3);
    } else if (process.bettingPhase == BettingPhase.FLOP_VALUE) {
      process.phase = HandPhase.DEALING_COMMUNITY;
      return buildDealCommunityCommand(process, 1);
    } else if (process.bettingPhase == BettingPhase.TURN_VALUE) {
      process.phase = HandPhase.DEALING_COMMUNITY;
      return buildDealCommunityCommand(process, 1);
    } else if (process.bettingPhase == BettingPhase.RIVER_VALUE) {
      return autoAwardPot(process);
    }
    return null;
  }

  private CommandBook buildDealCommunityCommand(HandProcess process, int count) {
    DealCommunityCards cmd = DealCommunityCards.newBuilder().setCount(count).build();
    return CommandBook.newBuilder()
        .setCover(
            Cover.newBuilder()
                .setDomain("hand")
                .setRoot(UUID.newBuilder().setValue(ByteString.copyFrom(process.handRoot))))
        .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd, TYPE_URL_PREFIX)))
        .build();
  }

  private CommandBook awardPotToLastPlayer(HandProcess process, PlayerState winner) {
    process.phase = HandPhase.COMPLETE;
    AwardPot cmd =
        AwardPot.newBuilder()
            .addAwards(
                PotAward.newBuilder()
                    .setPlayerRoot(ByteString.copyFrom(winner.playerRoot))
                    .setAmount(process.potTotal)
                    .setPotType("main"))
            .build();
    return CommandBook.newBuilder()
        .setCover(
            Cover.newBuilder()
                .setDomain("hand")
                .setRoot(UUID.newBuilder().setValue(ByteString.copyFrom(process.handRoot))))
        .addPages(CommandPage.newBuilder().setCommand(Any.pack(cmd, TYPE_URL_PREFIX)))
        .build();
  }

  private CommandBook autoAwardPot(HandProcess process) {
    List<PlayerState> playersInHand = new ArrayList<>();
    for (PlayerState p : process.players.values()) {
      if (!p.hasFolded) playersInHand.add(p);
    }
    if (playersInHand.isEmpty()) return null;

    long split = process.potTotal / playersInHand.size();
    long remainder = process.potTotal % playersInHand.size();

    AwardPot.Builder cmdBuilder = AwardPot.newBuilder();
    for (int i = 0; i < playersInHand.size(); i++) {
      PlayerState player = playersInHand.get(i);
      long amount = split + (i < remainder ? 1 : 0);
      cmdBuilder.addAwards(
          PotAward.newBuilder()
              .setPlayerRoot(ByteString.copyFrom(player.playerRoot))
              .setAmount(amount)
              .setPotType("main"));
    }
    process.phase = HandPhase.COMPLETE;

    return CommandBook.newBuilder()
        .setCover(
            Cover.newBuilder()
                .setDomain("hand")
                .setRoot(UUID.newBuilder().setValue(ByteString.copyFrom(process.handRoot))))
        .addPages(
            CommandPage.newBuilder().setCommand(Any.pack(cmdBuilder.build(), TYPE_URL_PREFIX)))
        .build();
  }

  private HandProcess findProcessByPlayer(byte[] playerRoot) {
    for (HandProcess process : processes.values()) {
      for (PlayerState player : process.players.values()) {
        if (Arrays.equals(player.playerRoot, playerRoot)) {
          return process;
        }
      }
    }
    return null;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  // --- In-process state ---

  enum HandPhase {
    DEALING,
    POSTING_BLINDS,
    BETTING,
    DEALING_COMMUNITY,
    COMPLETE
  }

  static class HandProcess {
    String handId;
    byte[] handRoot;
    long handNumber;
    int dealerPosition;
    int smallBlindPosition;
    int bigBlindPosition;
    long smallBlind;
    long bigBlind;
    HandPhase phase = HandPhase.DEALING;
    Map<Integer, PlayerState> players = new HashMap<>();
    List<Integer> activePositions = new ArrayList<>();
    boolean smallBlindPosted;
    boolean bigBlindPosted;
    long currentBet;
    long minRaise;
    long potTotal;
    int actionOn;
    int lastAggressor;
    Instant actionStartedAt;
    int communityCardCount;
    int bettingPhase;
  }

  static class PlayerState {
    byte[] playerRoot;
    int position;
    long stack;
    long betThisRound;
    long totalInvested;
    boolean hasActed;
    boolean hasFolded;
    boolean allIn;
  }
}
