package dev.angzarr.examples.tournament.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.state.TournamentState;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LifecycleHandlerTest {

  @Test
  void advanceBlindRejectsNotRunning() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());

    assertThatThrownBy(
            () ->
                LifecycleHandler.handleAdvanceBlind(AdvanceBlindLevel.getDefaultInstance(), state))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("not running");
  }

  @Test
  void advanceBlindIncrementsLevel() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .addAllBlindStructure(
                Arrays.asList(
                    BlindLevel.newBuilder().setLevel(1).setSmallBlind(25).setBigBlind(50).build(),
                    BlindLevel.newBuilder().setLevel(2).setSmallBlind(50).setBigBlind(100).build()))
            .build());
    state.applyTournamentStarted(TournamentStarted.getDefaultInstance());
    state.applyBlindAdvanced(BlindLevelAdvanced.newBuilder().setLevel(1).build());

    BlindLevelAdvanced result =
        LifecycleHandler.handleAdvanceBlind(AdvanceBlindLevel.getDefaultInstance(), state);

    assertThat(result.getLevel()).isEqualTo(2);
    assertThat(result.getSmallBlind()).isEqualTo(50);
    assertThat(result.getBigBlind()).isEqualTo(100);
  }

  @Test
  void eliminateRejectsNotRunning() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());

    assertThatThrownBy(
            () ->
                LifecycleHandler.handleEliminate(
                    EliminatePlayer.newBuilder()
                        .setPlayerRoot(ByteString.copyFrom(new byte[] {1}))
                        .build(),
                    state))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("not running");
  }

  @Test
  void eliminateRejectsUnregistered() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());
    state.applyTournamentStarted(TournamentStarted.getDefaultInstance());

    assertThatThrownBy(
            () ->
                LifecycleHandler.handleEliminate(
                    EliminatePlayer.newBuilder()
                        .setPlayerRoot(ByteString.copyFrom(new byte[] {1, 2, 3}))
                        .build(),
                    state))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("not registered");
  }

  @Test
  void eliminateSetsFinishPosition() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());
    state.applyRegistrationOpened(RegistrationOpened.getDefaultInstance());
    state.applyPlayerEnrolled(
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(new byte[] {1, 2, 3}))
            .setFeePaid(1000)
            .build());
    state.applyPlayerEnrolled(
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(new byte[] {4, 5, 6}))
            .setFeePaid(1000)
            .build());
    state.applyTournamentStarted(TournamentStarted.getDefaultInstance());

    PlayerEliminated result =
        LifecycleHandler.handleEliminate(
            EliminatePlayer.newBuilder()
                .setPlayerRoot(ByteString.copyFrom(new byte[] {1, 2, 3}))
                .build(),
            state);

    assertThat(result.getFinishPosition()).isEqualTo(2); // 2 players remaining
  }

  @Test
  void pauseRejectsNotRunning() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());

    assertThatThrownBy(
            () ->
                LifecycleHandler.handlePause(
                    PauseTournament.newBuilder().setReason("break").build(), state))
        .isInstanceOf(Errors.CommandRejectedError.class);
  }

  @Test
  void pauseSetsReason() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());
    state.applyTournamentStarted(TournamentStarted.getDefaultInstance());

    TournamentPaused result =
        LifecycleHandler.handlePause(
            PauseTournament.newBuilder().setReason("Dinner break").build(), state);

    assertThat(result.getReason()).isEqualTo("Dinner break");
  }

  @Test
  void resumeRejectsNotPaused() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());
    state.applyTournamentStarted(TournamentStarted.getDefaultInstance());

    assertThatThrownBy(
            () -> LifecycleHandler.handleResume(ResumeTournament.getDefaultInstance(), state))
        .isInstanceOf(Errors.CommandRejectedError.class);
  }

  @Test
  void resumeSuccess() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());
    state.applyTournamentStarted(TournamentStarted.getDefaultInstance());
    state.applyPaused(TournamentPaused.newBuilder().setReason("break").build());

    TournamentResumed result =
        LifecycleHandler.handleResume(ResumeTournament.getDefaultInstance(), state);

    assertThat(result.hasResumedAt()).isTrue();
  }
}
