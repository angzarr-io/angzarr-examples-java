package dev.angzarr.examples.tournament;

import com.google.protobuf.Message;
import dev.angzarr.client.CommandHandler;
import dev.angzarr.client.Errors;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.handlers.*;
import dev.angzarr.examples.tournament.state.TournamentState;

/**
 * Tournament aggregate with event sourcing (OO pattern).
 *
 * <p>Manages tournament lifecycle, registration, blind levels, eliminations, and rebuys.
 *
 * <p>This OO-style aggregate wraps the functional handlers for use with the annotation-based
 * CommandHandler framework.
 */
public class Tournament extends CommandHandler<TournamentState> {

  public static final String DOMAIN = "tournament";

  public Tournament() {
    super();
  }

  public Tournament(dev.angzarr.EventBook eventBook) {
    super(eventBook);
  }

  @Override
  public String getDomain() {
    return DOMAIN;
  }

  @Override
  protected TournamentState createEmptyState() {
    return new TournamentState();
  }

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
  public void applyTournamentPlayerEnrolled(
      TournamentState state, TournamentPlayerEnrolled event) {
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
  public TournamentCreated handleCreateTournament(CreateTournament cmd) {
    return CreateHandler.handle(cmd, getState());
  }

  @Handles(OpenRegistration.class)
  public RegistrationOpened handleOpenRegistration(OpenRegistration cmd) {
    return RegistrationHandler.handleOpen(cmd, getState());
  }

  @Handles(CloseRegistration.class)
  public RegistrationClosed handleCloseRegistration(CloseRegistration cmd) {
    return RegistrationHandler.handleClose(cmd, getState());
  }

  @Handles(EnrollPlayer.class)
  public Message handleEnrollPlayer(EnrollPlayer cmd) {
    return RegistrationHandler.handleEnroll(cmd, getState());
  }

  @Handles(AdvanceBlindLevel.class)
  public BlindLevelAdvanced handleAdvanceBlindLevel(AdvanceBlindLevel cmd) {
    return LifecycleHandler.handleAdvanceBlind(cmd, getState());
  }

  @Handles(EliminatePlayer.class)
  public PlayerEliminated handleEliminatePlayer(EliminatePlayer cmd) {
    return LifecycleHandler.handleEliminate(cmd, getState());
  }

  @Handles(PauseTournament.class)
  public TournamentPaused handlePauseTournament(PauseTournament cmd) {
    return LifecycleHandler.handlePause(cmd, getState());
  }

  @Handles(ResumeTournament.class)
  public TournamentResumed handleResumeTournament(ResumeTournament cmd) {
    return LifecycleHandler.handleResume(cmd, getState());
  }

  @Handles(ProcessRebuy.class)
  public Message handleProcessRebuy(ProcessRebuy cmd) {
    return RebuyHandler.handle(cmd, getState());
  }
}
