package dev.angzarr.examples.tournament.handlers;

import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.state.TournamentState;
import java.time.Instant;

public final class RegistrationHandler {

  private RegistrationHandler() {}

  public static RegistrationOpened handleOpen(OpenRegistration cmd, TournamentState state) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament does not exist");
    }
    if (state.isRegistrationOpen()) {
      throw Errors.CommandRejectedError.preconditionFailed("Registration already open");
    }
    if (state.isRunning()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament is running");
    }

    return RegistrationOpened.newBuilder().setOpenedAt(now()).build();
  }

  public static RegistrationClosed handleClose(CloseRegistration cmd, TournamentState state) {
    if (!state.isRegistrationOpen()) {
      throw Errors.CommandRejectedError.preconditionFailed("Registration not open");
    }

    return RegistrationClosed.newBuilder()
        .setTotalRegistrations(state.getRegisteredPlayers().size())
        .setClosedAt(now())
        .build();
  }

  /**
   * Handle EnrollPlayer — returns either enrolled or rejected event. Returns Object to support
   * dual-event pattern (enrolled OR rejected).
   */
  public static com.google.protobuf.Message handleEnroll(EnrollPlayer cmd, TournamentState state) {
    String rootHex = bytesToHex(cmd.getPlayerRoot().toByteArray());

    if (!state.isRegistrationOpen()) {
      return TournamentEnrollmentRejected.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setReason("closed")
          .setRejectedAt(now())
          .build();
    }
    if (state.isFull()) {
      return TournamentEnrollmentRejected.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setReason("full")
          .setRejectedAt(now())
          .build();
    }
    if (state.isPlayerRegistered(rootHex)) {
      return TournamentEnrollmentRejected.newBuilder()
          .setPlayerRoot(cmd.getPlayerRoot())
          .setReservationId(cmd.getReservationId())
          .setReason("already_registered")
          .setRejectedAt(now())
          .build();
    }

    return TournamentPlayerEnrolled.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setReservationId(cmd.getReservationId())
        .setFeePaid(state.getBuyIn())
        .setStartingStack(state.getStartingStack())
        .setRegistrationNumber(state.getRegisteredPlayers().size() + 1)
        .setEnrolledAt(now())
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
