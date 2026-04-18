package dev.angzarr.examples.tournament;

import com.google.protobuf.Message;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.handlers.*;
import dev.angzarr.examples.tournament.state.TournamentState;

/**
 * Tournament aggregate — Tier 5 annotation-driven. Manages tournament lifecycle, registration,
 * blind levels, eliminations, and rebuys.
 */
@Aggregate(domain = "tournament", state = TournamentState.class)
public class Tournament {

  public static final String DOMAIN = "tournament";

  // --- Event appliers ---

  @Applies(TournamentCreated.class)
  public void applyTournamentCreated(TournamentState state, TournamentCreated event) {
    state.applyCreated(event);
  }

  @Applies(RegistrationOpened.class)
  public void applyRegistrationOpened(TournamentState state, RegistrationOpened event) {
    state.applyRegistrationOpened(event);
  }

  @Applies(RegistrationClosed.class)
  public void applyRegistrationClosed(TournamentState state, RegistrationClosed event) {
    state.applyRegistrationClosed(event);
  }

  @Applies(TournamentPlayerEnrolled.class)
  public void applyTournamentPlayerEnrolled(TournamentState state, TournamentPlayerEnrolled event) {
    state.applyPlayerEnrolled(event);
  }

  @Applies(TournamentEnrollmentRejected.class)
  public void applyTournamentEnrollmentRejected(
      TournamentState state, TournamentEnrollmentRejected event) {
    // No state change for rejections
  }

  @Applies(TournamentStarted.class)
  public void applyTournamentStarted(TournamentState state, TournamentStarted event) {
    state.applyTournamentStarted(event);
  }

  @Applies(BlindLevelAdvanced.class)
  public void applyBlindLevelAdvanced(TournamentState state, BlindLevelAdvanced event) {
    state.applyBlindAdvanced(event);
  }

  @Applies(PlayerEliminated.class)
  public void applyPlayerEliminated(TournamentState state, PlayerEliminated event) {
    state.applyPlayerEliminated(event);
  }

  @Applies(RebuyProcessed.class)
  public void applyRebuyProcessed(TournamentState state, RebuyProcessed event) {
    state.applyRebuyProcessed(event);
  }

  @Applies(RebuyDenied.class)
  public void applyRebuyDenied(TournamentState state, RebuyDenied event) {
    // No state change for denials
  }

  @Applies(TournamentPaused.class)
  public void applyTournamentPaused(TournamentState state, TournamentPaused event) {
    state.applyPaused(event);
  }

  @Applies(TournamentResumed.class)
  public void applyTournamentResumed(TournamentState state, TournamentResumed event) {
    state.applyResumed(event);
  }

  @Applies(TournamentCompleted.class)
  public void applyTournamentCompleted(TournamentState state, TournamentCompleted event) {
    state.applyCompleted(event);
  }

  // --- Command handlers ---

  @Handles(CreateTournament.class)
  public TournamentCreated handleCreateTournament(
      CreateTournament cmd, TournamentState state, long seq) {
    return CreateHandler.handle(cmd, state);
  }

  @Handles(OpenRegistration.class)
  public RegistrationOpened handleOpenRegistration(
      OpenRegistration cmd, TournamentState state, long seq) {
    return RegistrationHandler.handleOpen(cmd, state);
  }

  @Handles(CloseRegistration.class)
  public RegistrationClosed handleCloseRegistration(
      CloseRegistration cmd, TournamentState state, long seq) {
    return RegistrationHandler.handleClose(cmd, state);
  }

  @Handles(EnrollPlayer.class)
  public Message handleEnrollPlayer(EnrollPlayer cmd, TournamentState state, long seq) {
    return RegistrationHandler.handleEnroll(cmd, state);
  }

  @Handles(AdvanceBlindLevel.class)
  public BlindLevelAdvanced handleAdvanceBlindLevel(
      AdvanceBlindLevel cmd, TournamentState state, long seq) {
    return LifecycleHandler.handleAdvanceBlind(cmd, state);
  }

  @Handles(EliminatePlayer.class)
  public PlayerEliminated handleEliminatePlayer(
      EliminatePlayer cmd, TournamentState state, long seq) {
    return LifecycleHandler.handleEliminate(cmd, state);
  }

  @Handles(PauseTournament.class)
  public TournamentPaused handlePauseTournament(
      PauseTournament cmd, TournamentState state, long seq) {
    return LifecycleHandler.handlePause(cmd, state);
  }

  @Handles(ResumeTournament.class)
  public TournamentResumed handleResumeTournament(
      ResumeTournament cmd, TournamentState state, long seq) {
    return LifecycleHandler.handleResume(cmd, state);
  }

  @Handles(ProcessRebuy.class)
  public Message handleProcessRebuy(ProcessRebuy cmd, TournamentState state, long seq) {
    return RebuyHandler.handle(cmd, state);
  }
}
