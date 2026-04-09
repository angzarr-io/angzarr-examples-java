package dev.angzarr.examples.client;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.angzarr.CommandResponse;
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

  // --- Lifecycle ---

  public void reset() {
    playerRoots.clear();
    tableRoots.clear();
    handRoots.clear();
    sequences.clear();
    lastResponse = null;
    lastError = null;
  }

  public void close() {
    client.close();
  }
}
