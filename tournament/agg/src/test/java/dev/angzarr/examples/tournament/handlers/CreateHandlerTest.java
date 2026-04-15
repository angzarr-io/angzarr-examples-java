package dev.angzarr.examples.tournament.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.client.Errors;
import dev.angzarr.examples.CreateTournament;
import dev.angzarr.examples.GameVariant;
import dev.angzarr.examples.TournamentCreated;
import dev.angzarr.examples.tournament.state.TournamentState;
import org.junit.jupiter.api.Test;

class CreateHandlerTest {

  @Test
  void rejectsExistingTournament() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Existing")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .setMinPlayers(2)
            .build());

    assertThatThrownBy(
            () ->
                CreateHandler.handle(
                    CreateTournament.newBuilder()
                        .setName("New")
                        .setBuyIn(500)
                        .setStartingStack(5000)
                        .setMaxPlayers(50)
                        .setMinPlayers(2)
                        .build(),
                    state))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void rejectsEmptyName() {
    assertThatThrownBy(
            () ->
                CreateHandler.handle(
                    CreateTournament.newBuilder()
                        .setName("")
                        .setBuyIn(1000)
                        .setStartingStack(10000)
                        .setMaxPlayers(100)
                        .build(),
                    new TournamentState()))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("name");
  }

  @Test
  void rejectsZeroBuyIn() {
    assertThatThrownBy(
            () ->
                CreateHandler.handle(
                    CreateTournament.newBuilder()
                        .setName("Test")
                        .setBuyIn(0)
                        .setStartingStack(10000)
                        .setMaxPlayers(100)
                        .build(),
                    new TournamentState()))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("positive");
  }

  @Test
  void rejectsZeroStartingStack() {
    assertThatThrownBy(
            () ->
                CreateHandler.handle(
                    CreateTournament.newBuilder()
                        .setName("Test")
                        .setBuyIn(1000)
                        .setStartingStack(0)
                        .setMaxPlayers(100)
                        .build(),
                    new TournamentState()))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("positive");
  }

  @Test
  void rejectsMaxPlayersLessThan2() {
    assertThatThrownBy(
            () ->
                CreateHandler.handle(
                    CreateTournament.newBuilder()
                        .setName("Test")
                        .setBuyIn(1000)
                        .setStartingStack(10000)
                        .setMaxPlayers(1)
                        .build(),
                    new TournamentState()))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("max_players");
  }

  @Test
  void setsAllFieldsOnSuccess() {
    TournamentCreated event =
        CreateHandler.handle(
            CreateTournament.newBuilder()
                .setName("Sunday Million")
                .setGameVariant(GameVariant.TEXAS_HOLDEM)
                .setBuyIn(1000)
                .setStartingStack(10000)
                .setMaxPlayers(100)
                .setMinPlayers(10)
                .build(),
            new TournamentState());

    assertThat(event.getName()).isEqualTo("Sunday Million");
    assertThat(event.getGameVariant()).isEqualTo(GameVariant.TEXAS_HOLDEM);
    assertThat(event.getBuyIn()).isEqualTo(1000);
    assertThat(event.getStartingStack()).isEqualTo(10000);
    assertThat(event.getMaxPlayers()).isEqualTo(100);
    assertThat(event.getMinPlayers()).isEqualTo(10);
    assertThat(event.hasCreatedAt()).isTrue();
  }
}
