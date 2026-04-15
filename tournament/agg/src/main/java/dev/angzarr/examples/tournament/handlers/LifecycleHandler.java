package dev.angzarr.examples.tournament.handlers;

import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.state.TournamentState;
import java.time.Instant;
import java.util.List;

public final class LifecycleHandler {

  private LifecycleHandler() {}

  public static BlindLevelAdvanced handleAdvanceBlind(AdvanceBlindLevel cmd, TournamentState state) {
    if (!state.isRunning()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament not running");
    }

    int nextLevel = state.getCurrentLevel() + 1;
    long sb = 0, bb = 0, ante = 0;
    List<BlindLevel> structure = state.getBlindStructure();
    if (!structure.isEmpty()) {
      int idx = nextLevel - 1;
      if (idx >= structure.size()) idx = structure.size() - 1;
      if (idx >= 0) {
        sb = structure.get(idx).getSmallBlind();
        bb = structure.get(idx).getBigBlind();
        ante = structure.get(idx).getAnte();
      }
    }

    return BlindLevelAdvanced.newBuilder()
        .setLevel(nextLevel)
        .setSmallBlind(sb)
        .setBigBlind(bb)
        .setAnte(ante)
        .setAdvancedAt(now())
        .build();
  }

  public static PlayerEliminated handleEliminate(EliminatePlayer cmd, TournamentState state) {
    if (!state.isRunning()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament not running");
    }

    String rootHex = bytesToHex(cmd.getPlayerRoot().toByteArray());
    if (!state.isPlayerRegistered(rootHex)) {
      throw Errors.CommandRejectedError.preconditionFailed("Player not registered");
    }

    return PlayerEliminated.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setFinishPosition(state.getPlayersRemaining())
        .setHandRoot(cmd.getHandRoot())
        .setEliminatedAt(now())
        .build();
  }

  public static TournamentPaused handlePause(PauseTournament cmd, TournamentState state) {
    if (!state.isRunning()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament not running");
    }

    return TournamentPaused.newBuilder()
        .setReason(cmd.getReason())
        .setPausedAt(now())
        .build();
  }

  public static TournamentResumed handleResume(ResumeTournament cmd, TournamentState state) {
    if (state.getStatus() != TournamentStatus.TOURNAMENT_PAUSED) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament not paused");
    }

    return TournamentResumed.newBuilder()
        .setResumedAt(now())
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
