package dev.angzarr.examples.tournament.handlers;

import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.state.TournamentState;
import java.time.Instant;

public final class RebuyHandler {

  private RebuyHandler() {}

  public static Message handle(ProcessRebuy cmd, TournamentState state) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament does not exist");
    }
    if (!state.isRunning()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament not running");
    }

    String rootHex = bytesToHex(cmd.getPlayerRoot().toByteArray());
    if (!state.isPlayerRegistered(rootHex)) {
      throw Errors.CommandRejectedError.preconditionFailed("Player not registered");
    }

    RebuyConfig config = state.getRebuyConfig();
    if (config == null || !config.getEnabled()) {
      return RebuyDenied.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setReason("rebuys_disabled")
          .setDeniedAt(now())
          .build();
    }

    if (state.getCurrentLevel() > config.getRebuyLevelCutoff()) {
      return RebuyDenied.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setReason("window_closed")
          .setDeniedAt(now())
          .build();
    }

    int rebuysUsed = state.playerRebuyCount(rootHex);
    if (config.getMaxRebuys() > 0 && rebuysUsed >= config.getMaxRebuys()) {
      return RebuyDenied.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setReason("max_reached")
          .setDeniedAt(now())
          .build();
    }

    return RebuyProcessed.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setReservationId(cmd.getReservationId())
        .setRebuyCost(config.getRebuyCost())
        .setChipsAdded(config.getRebuyChips())
        .setRebuyCount(rebuysUsed + 1)
        .setProcessedAt(now())
        .build();
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static Timestamp now() {
    Instant instant = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
