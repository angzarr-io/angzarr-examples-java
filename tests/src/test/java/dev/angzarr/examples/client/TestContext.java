package dev.angzarr.examples.client;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.angzarr.CascadeErrorMode;
import dev.angzarr.CommandResponse;
import dev.angzarr.SyncMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared test context (Cucumber world) for acceptance tests.
 *
 * <p>Holds the CommandClient instance, named entity roots, and the last command response. The
 * client implementation is chosen based on the PLAYER_URL environment variable: if set, a
 */
public class TestContext {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private final CommandClient client;

  /** Named player roots: "Alice" -> UUID. */
  private final Map<String, UUID> playerRoots = new HashMap<>();

  /** Named table roots: "Main" -> UUID. */
  private final Map<String, UUID> tableRoots = new HashMap<>();

  /** Named hand roots. */
  private final Map<String, UUID> handRoots = new HashMap<>();

  /** Sequence counters per (domain, root). */
  private final Map<String, Integer> sequences = new HashMap<>();

  /** Last command response for assertions. */
  private CommandResponse lastResponse;

  /** Last exception from a failed command. */
  private Exception lastError;

  /** Tracks most recently created table. */
  private String lastTableName;

  /** Tracks which table has the active hand. */
  private String currentHandTable;

  /** Deterministic deck seed for testing. */
  private String deckSeed;

  /** Config flags for sync mode scenarios. */
  private boolean tableHandSagaFail;

  private boolean handPlayerSagaFail;
  private boolean outputProjectorOK;
  private boolean deadLetterConfigured;
  private boolean handFlowPMRegistered;
  private boolean multipleSagasFail;
  private boolean domainNoSagas;
  private boolean monitoringBus;

  /** Timing for sync mode tests. */
  private long syncTestStartTime;

  public TestContext() {
    String playerUrl = System.getenv("PLAYER_URL");
    if (playerUrl == null || playerUrl.isEmpty()) {
      playerUrl = "localhost:1310";
    }
    this.client = new GrpcCommandClient(playerUrl);
  }

  public CommandClient getClient() {
    return client;
  }

  // --- Root management ---

  public UUID getOrCreatePlayerRoot(String name) {
    return playerRoots.computeIfAbsent(name, k -> UUID.randomUUID());
  }

  public UUID getPlayerRoot(String name) {
    UUID root = playerRoots.get(name);
    if (root == null) {
      throw new IllegalStateException("Unknown player: " + name);
    }
    return root;
  }

  public UUID getOrCreateTableRoot(String name) {
    return tableRoots.computeIfAbsent(name, k -> UUID.randomUUID());
  }

  public UUID getTableRoot(String name) {
    UUID root = tableRoots.get(name);
    if (root == null) {
      throw new IllegalStateException("Unknown table: " + name);
    }
    return root;
  }

  public UUID getOrCreateHandRoot(String name) {
    return handRoots.computeIfAbsent(name, k -> UUID.randomUUID());
  }

  // --- Sequence tracking ---

  public int getSequence(String domain, UUID root) {
    String key = domain + ":" + root;
    return sequences.getOrDefault(key, 0);
  }

  public void advanceSequence(String domain, UUID root, int eventCount) {
    String key = domain + ":" + root;
    int seq = sequences.getOrDefault(key, 0);
    sequences.put(key, seq + eventCount);
  }

  // --- Command sending ---

  /**
   * Send a command and store the response. Returns the response on success. Sequence is advanced
   * only on success, by the number of event pages in the response.
   *
   * @throws RuntimeException if the command fails (also stored in lastError)
   */
  public CommandResponse sendCommand(String domain, UUID root, Message command) {
    Any commandAny =
        Any.newBuilder()
            .setTypeUrl(TYPE_URL_PREFIX + command.getDescriptorForType().getFullName())
            .setValue(command.toByteString())
            .build();
    int seq = getSequence(domain, root);
    try {
      lastResponse = client.sendCommand(domain, root, commandAny, seq);
      lastError = null;
      if (lastResponse.hasEvents()) {
        advanceSequence(domain, root, lastResponse.getEvents().getPagesCount());
      }
      return lastResponse;
    } catch (Exception e) {
      lastError = e;
      lastResponse = null;
      throw e;
    }
  }

  /**
   * Send a command, tolerating failure. Stores error but does not throw.
   *
   * @return true if the command succeeded, false if it failed
   */
  public boolean trySendCommand(String domain, UUID root, Message command) {
    try {
      sendCommand(domain, root, command);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public CommandResponse getLastResponse() {
    return lastResponse;
  }

  public Exception getLastError() {
    return lastError;
  }

  // --- New field getters/setters ---

  public String getLastTableName() {
    return lastTableName;
  }

  public void setLastTableName(String lastTableName) {
    this.lastTableName = lastTableName;
  }

  public String getCurrentHandTable() {
    return currentHandTable;
  }

  public void setCurrentHandTable(String currentHandTable) {
    this.currentHandTable = currentHandTable;
  }

  public String getDeckSeed() {
    return deckSeed;
  }

  public void setDeckSeed(String deckSeed) {
    this.deckSeed = deckSeed;
  }

  public boolean isTableHandSagaFail() {
    return tableHandSagaFail;
  }

  public void setTableHandSagaFail(boolean tableHandSagaFail) {
    this.tableHandSagaFail = tableHandSagaFail;
  }

  public boolean isHandPlayerSagaFail() {
    return handPlayerSagaFail;
  }

  public void setHandPlayerSagaFail(boolean handPlayerSagaFail) {
    this.handPlayerSagaFail = handPlayerSagaFail;
  }

  public boolean isOutputProjectorOK() {
    return outputProjectorOK;
  }

  public void setOutputProjectorOK(boolean outputProjectorOK) {
    this.outputProjectorOK = outputProjectorOK;
  }

  public boolean isDeadLetterConfigured() {
    return deadLetterConfigured;
  }

  public void setDeadLetterConfigured(boolean deadLetterConfigured) {
    this.deadLetterConfigured = deadLetterConfigured;
  }

  public boolean isHandFlowPMRegistered() {
    return handFlowPMRegistered;
  }

  public void setHandFlowPMRegistered(boolean handFlowPMRegistered) {
    this.handFlowPMRegistered = handFlowPMRegistered;
  }

  public boolean isMultipleSagasFail() {
    return multipleSagasFail;
  }

  public void setMultipleSagasFail(boolean multipleSagasFail) {
    this.multipleSagasFail = multipleSagasFail;
  }

  public boolean isDomainNoSagas() {
    return domainNoSagas;
  }

  public void setDomainNoSagas(boolean domainNoSagas) {
    this.domainNoSagas = domainNoSagas;
  }

  public boolean isMonitoringBus() {
    return monitoringBus;
  }

  public void setMonitoringBus(boolean monitoringBus) {
    this.monitoringBus = monitoringBus;
  }

  public long getSyncTestStartTime() {
    return syncTestStartTime;
  }

  public void setSyncTestStartTime(long syncTestStartTime) {
    this.syncTestStartTime = syncTestStartTime;
  }

  // --- Sync mode command sending ---

  /**
   * Send a command with explicit sync mode and cascade error mode.
   *
   * @return The command response on success
   * @throws RuntimeException if the command fails
   */
  public CommandResponse sendCommandWithMode(
      String domain, UUID root, Message command, SyncMode syncMode, CascadeErrorMode cascadeErrorMode) {
    Any commandAny =
        Any.newBuilder()
            .setTypeUrl(TYPE_URL_PREFIX + command.getDescriptorForType().getFullName())
            .setValue(command.toByteString())
            .build();
    int seq = getSequence(domain, root);
    try {
      lastResponse = client.sendCommand(domain, root, commandAny, seq, syncMode, cascadeErrorMode);
      lastError = null;
      if (lastResponse.hasEvents()) {
        advanceSequence(domain, root, lastResponse.getEvents().getPagesCount());
      }
      return lastResponse;
    } catch (Exception e) {
      lastError = e;
      lastResponse = null;
      throw e;
    }
  }

  /**
   * Send a command with sync mode, tolerating failure.
   *
   * @return true if the command succeeded, false if it failed
   */
  public boolean trySendCommandWithMode(
      String domain, UUID root, Message command, SyncMode syncMode, CascadeErrorMode cascadeErrorMode) {
    try {
      sendCommandWithMode(domain, root, command, syncMode, cascadeErrorMode);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  // --- Lifecycle ---

  public void reset() {
    playerRoots.clear();
    tableRoots.clear();
    handRoots.clear();
    sequences.clear();
    lastResponse = null;
    lastError = null;
    lastTableName = null;
    currentHandTable = null;
    deckSeed = null;
    tableHandSagaFail = false;
    handPlayerSagaFail = false;
    outputProjectorOK = false;
    deadLetterConfigured = false;
    handFlowPMRegistered = false;
    multipleSagasFail = false;
    domainNoSagas = false;
    monitoringBus = false;
    syncTestStartTime = 0;
  }

  public void close() {
    client.close();
  }
}
