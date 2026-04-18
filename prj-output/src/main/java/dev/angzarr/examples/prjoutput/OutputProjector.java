package dev.angzarr.examples.prjoutput;

import com.google.protobuf.ByteString;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.examples.ActionTaken;
import dev.angzarr.examples.BlindPosted;
import dev.angzarr.examples.CardsDealt;
import dev.angzarr.examples.FundsDeposited;
import dev.angzarr.examples.HandComplete;
import dev.angzarr.examples.HandStarted;
import dev.angzarr.examples.PlayerJoined;
import dev.angzarr.examples.PlayerRegistered;
import dev.angzarr.examples.PotAwarded;
import dev.angzarr.examples.TableCreated;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Projector: Output — Tier 5 annotation-driven. Writes formatted game logs to a file. Handler
 * methods are side-effect only (void return); the framework wraps dispatch into a Projection
 * response.
 */
@Projector(
    name = "output",
    domains = {"player", "table", "hand"})
public class OutputProjector {

  private static final String LOG_FILE =
      System.getenv().getOrDefault("HAND_LOG_FILE", "hand_log.txt");

  private static PrintWriter logWriter;

  private static synchronized PrintWriter getLogWriter() {
    if (logWriter == null) {
      try {
        logWriter = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)));
      } catch (IOException e) {
        System.err.println("Failed to open log file: " + e.getMessage());
      }
    }
    return logWriter;
  }

  private void writeLog(String msg) {
    PrintWriter writer = getLogWriter();
    if (writer != null) {
      String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
      writer.println("[" + timestamp + "] " + msg);
      writer.flush();
    }
  }

  private String truncateId(ByteString playerRoot) {
    byte[] bytes = playerRoot.toByteArray();
    if (bytes.length >= 4) {
      return String.format(
          "%02x%02x%02x%02x", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF);
    }
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }

  @Handles(PlayerRegistered.class)
  public void onPlayerRegistered(PlayerRegistered event) {
    writeLog(String.format("PLAYER registered: %s (%s)", event.getDisplayName(), event.getEmail()));
  }

  @Handles(FundsDeposited.class)
  public void onFundsDeposited(FundsDeposited event) {
    long amount = event.hasAmount() ? event.getAmount().getAmount() : 0;
    long newBalance = event.hasNewBalance() ? event.getNewBalance().getAmount() : 0;
    writeLog(String.format("PLAYER deposited %d, balance: %d", amount, newBalance));
  }

  @Handles(TableCreated.class)
  public void onTableCreated(TableCreated event) {
    writeLog(String.format("TABLE created: %s (%s)", event.getTableName(), event.getGameVariant()));
  }

  @Handles(PlayerJoined.class)
  public void onPlayerJoined(PlayerJoined event) {
    String playerId = truncateId(event.getPlayerRoot());
    writeLog(String.format("TABLE player %s joined with %d chips", playerId, event.getStack()));
  }

  @Handles(HandStarted.class)
  public void onHandStarted(HandStarted event) {
    writeLog(
        String.format(
            "TABLE hand #%d started, %d players, dealer at position %d",
            event.getHandNumber(), event.getActivePlayersCount(), event.getDealerPosition()));
  }

  @Handles(CardsDealt.class)
  public void onCardsDealt(CardsDealt event) {
    writeLog(String.format("HAND cards dealt to %d players", event.getPlayerCardsCount()));
  }

  @Handles(BlindPosted.class)
  public void onBlindPosted(BlindPosted event) {
    String playerId = truncateId(event.getPlayerRoot());
    writeLog(
        String.format(
            "HAND player %s posted %s blind: %d",
            playerId, event.getBlindType(), event.getAmount()));
  }

  @Handles(ActionTaken.class)
  public void onActionTaken(ActionTaken event) {
    String playerId = truncateId(event.getPlayerRoot());
    writeLog(
        String.format("HAND player %s: %s %d", playerId, event.getAction(), event.getAmount()));
  }

  @Handles(PotAwarded.class)
  public void onPotAwarded(PotAwarded event) {
    String winners =
        event.getWinnersList().stream()
            .map(w -> truncateId(w.getPlayerRoot()) + " wins " + w.getAmount())
            .collect(Collectors.joining(", "));
    writeLog(String.format("HAND pot awarded: %s", winners));
  }

  @Handles(HandComplete.class)
  public void onHandComplete(HandComplete event) {
    writeLog(String.format("HAND #%d complete", event.getHandNumber()));
  }
}
