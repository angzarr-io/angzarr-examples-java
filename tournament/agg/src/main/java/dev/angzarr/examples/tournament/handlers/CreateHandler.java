package dev.angzarr.examples.tournament.handlers;

import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.CreateTournament;
import dev.angzarr.examples.TournamentCreated;
import dev.angzarr.examples.tournament.state.TournamentState;
import java.time.Instant;

public final class CreateHandler {

  private CreateHandler() {}

  public static TournamentCreated handle(CreateTournament cmd, TournamentState state) {
    // Guard
    if (state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Tournament already exists");
    }

    // Validate
    if (cmd.getName().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("name is required");
    }
    if (cmd.getBuyIn() <= 0) {
      throw Errors.CommandRejectedError.invalidArgument("buy_in must be positive");
    }
    if (cmd.getStartingStack() <= 0) {
      throw Errors.CommandRejectedError.invalidArgument("starting_stack must be positive");
    }
    if (cmd.getMaxPlayers() < 2) {
      throw Errors.CommandRejectedError.invalidArgument("max_players must be at least 2");
    }

    // Compute
    TournamentCreated.Builder builder =
        TournamentCreated.newBuilder()
            .setName(cmd.getName())
            .setGameVariant(cmd.getGameVariant())
            .setBuyIn(cmd.getBuyIn())
            .setStartingStack(cmd.getStartingStack())
            .setMaxPlayers(cmd.getMaxPlayers())
            .setMinPlayers(cmd.getMinPlayers())
            .setCreatedAt(now());

    if (cmd.hasRebuyConfig()) {
      builder.setRebuyConfig(cmd.getRebuyConfig());
    }
    builder.addAllBlindStructure(cmd.getBlindStructureList());

    return builder.build();
  }

  private static Timestamp now() {
    Instant instant = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
