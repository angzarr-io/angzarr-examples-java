package dev.angzarr.examples.tournament.state;

import dev.angzarr.examples.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal state for the Tournament aggregate.
 *
 * <p>Tracks tournament configuration, registration status, enrolled players,
 * blind level progression, and elimination tracking.
 */
public class TournamentState {

  private String name = "";
  private GameVariant gameVariant = GameVariant.GAME_VARIANT_UNSPECIFIED;
  private TournamentStatus status = TournamentStatus.TOURNAMENT_STATUS_UNSPECIFIED;
  private long buyIn = 0;
  private long startingStack = 0;
  private int maxPlayers = 0;
  private int minPlayers = 0;
  private RebuyConfig rebuyConfig = null;
  private final List<BlindLevel> blindStructure = new ArrayList<>();
  private int currentLevel = 0;
  private final Map<String, PlayerRegistration> registeredPlayers = new HashMap<>();
  private int playersRemaining = 0;
  private long totalPrizePool = 0;

  public boolean exists() { return !name.isEmpty(); }
  public boolean isRegistrationOpen() { return status == TournamentStatus.TOURNAMENT_REGISTRATION_OPEN; }
  public boolean isRunning() { return status == TournamentStatus.TOURNAMENT_RUNNING; }
  public boolean isFull() { return registeredPlayers.size() >= maxPlayers; }
  public boolean isPlayerRegistered(String rootHex) { return registeredPlayers.containsKey(rootHex); }

  public int playerRebuyCount(String rootHex) {
    PlayerRegistration reg = registeredPlayers.get(rootHex);
    return reg != null ? reg.getRebuysUsed() : 0;
  }

  // Getters
  public String getName() { return name; }
  public TournamentStatus getStatus() { return status; }
  public long getBuyIn() { return buyIn; }
  public long getStartingStack() { return startingStack; }
  public int getMaxPlayers() { return maxPlayers; }
  public int getMinPlayers() { return minPlayers; }
  public RebuyConfig getRebuyConfig() { return rebuyConfig; }
  public List<BlindLevel> getBlindStructure() { return blindStructure; }
  public int getCurrentLevel() { return currentLevel; }
  public Map<String, PlayerRegistration> getRegisteredPlayers() { return registeredPlayers; }
  public int getPlayersRemaining() { return playersRemaining; }
  public long getTotalPrizePool() { return totalPrizePool; }

  // Event appliers

  public void applyCreated(TournamentCreated event) {
    this.name = event.getName();
    this.gameVariant = event.getGameVariant();
    this.status = TournamentStatus.TOURNAMENT_CREATED;
    this.buyIn = event.getBuyIn();
    this.startingStack = event.getStartingStack();
    this.maxPlayers = event.getMaxPlayers();
    this.minPlayers = event.getMinPlayers();
    this.rebuyConfig = event.hasRebuyConfig() ? event.getRebuyConfig() : null;
    this.blindStructure.clear();
    this.blindStructure.addAll(event.getBlindStructureList());
    this.currentLevel = 0;
    this.playersRemaining = 0;
    this.totalPrizePool = 0;
  }

  public void applyRegistrationOpened(RegistrationOpened event) {
    this.status = TournamentStatus.TOURNAMENT_REGISTRATION_OPEN;
  }

  public void applyRegistrationClosed(RegistrationClosed event) {
    this.status = TournamentStatus.TOURNAMENT_CREATED;
  }

  public void applyPlayerEnrolled(TournamentPlayerEnrolled event) {
    String rootHex = bytesToHex(event.getPlayerRoot().toByteArray());
    PlayerRegistration reg = PlayerRegistration.newBuilder()
        .setPlayerRoot(event.getPlayerRoot())
        .setFeePaid(event.getFeePaid())
        .setStartingStack(event.getStartingStack())
        .build();
    registeredPlayers.put(rootHex, reg);
    playersRemaining++;
    totalPrizePool += event.getFeePaid();
  }

  public void applyTournamentStarted(TournamentStarted event) {
    this.status = TournamentStatus.TOURNAMENT_RUNNING;
  }

  public void applyRebuyProcessed(RebuyProcessed event) {
    String rootHex = bytesToHex(event.getPlayerRoot().toByteArray());
    PlayerRegistration existing = registeredPlayers.get(rootHex);
    if (existing != null) {
      registeredPlayers.put(rootHex, existing.toBuilder()
          .setRebuysUsed(event.getRebuyCount())
          .build());
    }
    totalPrizePool += event.getRebuyCost();
  }

  public void applyBlindAdvanced(BlindLevelAdvanced event) {
    this.currentLevel = event.getLevel();
  }

  public void applyPlayerEliminated(PlayerEliminated event) {
    String rootHex = bytesToHex(event.getPlayerRoot().toByteArray());
    registeredPlayers.remove(rootHex);
    playersRemaining--;
  }

  public void applyPaused(TournamentPaused event) {
    this.status = TournamentStatus.TOURNAMENT_PAUSED;
  }

  public void applyResumed(TournamentResumed event) {
    this.status = TournamentStatus.TOURNAMENT_RUNNING;
  }

  public void applyCompleted(TournamentCompleted event) {
    this.status = TournamentStatus.TOURNAMENT_COMPLETED;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
