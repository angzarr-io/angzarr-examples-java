package dev.angzarr.examples.tournament.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.state.TournamentState;
import org.junit.jupiter.api.Test;

class RegistrationHandlerTest {

  private TournamentState openState() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .setMinPlayers(2)
            .build());
    state.applyRegistrationOpened(RegistrationOpened.getDefaultInstance());
    return state;
  }

  @Test
  void openRejectsNonExistent() {
    assertThatThrownBy(
            () ->
                RegistrationHandler.handleOpen(
                    OpenRegistration.getDefaultInstance(), new TournamentState()))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void openRejectsAlreadyOpen() {
    TournamentState state = openState();

    assertThatThrownBy(
            () -> RegistrationHandler.handleOpen(OpenRegistration.getDefaultInstance(), state))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessageContaining("already open");
  }

  @Test
  void closeIncludesTotalRegistrations() {
    TournamentState state = openState();
    state.applyPlayerEnrolled(
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(new byte[] {1}))
            .setFeePaid(1000)
            .setStartingStack(10000)
            .setRegistrationNumber(1)
            .build());
    state.applyPlayerEnrolled(
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(new byte[] {2}))
            .setFeePaid(1000)
            .setStartingStack(10000)
            .setRegistrationNumber(2)
            .build());

    RegistrationClosed result =
        RegistrationHandler.handleClose(CloseRegistration.getDefaultInstance(), state);

    assertThat(result.getTotalRegistrations()).isEqualTo(2);
  }

  @Test
  void enrollRejectsClosedRegistration() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .build());

    Message result =
        RegistrationHandler.handleEnroll(
            EnrollPlayer.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[] {1})).build(),
            state);

    assertThat(result).isInstanceOf(TournamentEnrollmentRejected.class);
    assertThat(((TournamentEnrollmentRejected) result).getReason()).isEqualTo("closed");
  }

  @Test
  void enrollRejectsFull() {
    TournamentState state = new TournamentState();
    state.applyCreated(
        TournamentCreated.newBuilder()
            .setName("Test")
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(2)
            .build());
    state.applyRegistrationOpened(RegistrationOpened.getDefaultInstance());
    state.applyPlayerEnrolled(
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(new byte[] {1}))
            .setFeePaid(1000)
            .build());
    state.applyPlayerEnrolled(
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(ByteString.copyFrom(new byte[] {2}))
            .setFeePaid(1000)
            .build());

    Message result =
        RegistrationHandler.handleEnroll(
            EnrollPlayer.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[] {3})).build(),
            state);

    assertThat(result).isInstanceOf(TournamentEnrollmentRejected.class);
    assertThat(((TournamentEnrollmentRejected) result).getReason()).isEqualTo("full");
  }

  @Test
  void enrollSuccess() {
    TournamentState state = openState();

    Message result =
        RegistrationHandler.handleEnroll(
            EnrollPlayer.newBuilder()
                .setPlayerRoot(ByteString.copyFrom(new byte[] {1, 2, 3}))
                .build(),
            state);

    assertThat(result).isInstanceOf(TournamentPlayerEnrolled.class);
    TournamentPlayerEnrolled enrolled = (TournamentPlayerEnrolled) result;
    assertThat(enrolled.getFeePaid()).isEqualTo(1000);
    assertThat(enrolled.getStartingStack()).isEqualTo(10000);
    assertThat(enrolled.getRegistrationNumber()).isEqualTo(1);
  }
}
