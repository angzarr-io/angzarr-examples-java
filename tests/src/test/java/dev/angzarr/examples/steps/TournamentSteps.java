package dev.angzarr.examples.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.handlers.CreateHandler;
import dev.angzarr.examples.tournament.handlers.LifecycleHandler;
import dev.angzarr.examples.tournament.handlers.RebuyHandler;
import dev.angzarr.examples.tournament.handlers.RegistrationHandler;
import dev.angzarr.examples.tournament.state.TournamentState;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Cucumber step definitions for Tournament aggregate tests. */
public class TournamentSteps {

  private TournamentState state;
  private Message resultEvent;
  private Errors.CommandRejectedError rejectedError;

  @Before
  public void setup() {
    state = new TournamentState();
    resultEvent = null;
    rejectedError = null;
  }

  // --- Helpers ---

  private static ByteString playerRoot(String name) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] hash = md.digest(("player:" + name).getBytes(StandardCharsets.UTF_8));
      byte[] uuid = new byte[16];
      System.arraycopy(hash, 0, uuid, 0, 16);
      return ByteString.copyFrom(uuid);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void handleCommand(java.util.function.Supplier<Message> handler) {
    try {
      resultEvent = handler.get();
      rejectedError = null;
      CommonSteps.setLastRejectedError(null);
      // Apply event to state for chaining
      applyEvent(resultEvent);
    } catch (Errors.CommandRejectedError e) {
      resultEvent = null;
      rejectedError = e;
      CommonSteps.setLastRejectedError(e);
    }
  }

  private void applyEvent(Message event) {
    if (event instanceof TournamentCreated e) state.applyCreated(e);
    else if (event instanceof RegistrationOpened e) state.applyRegistrationOpened(e);
    else if (event instanceof RegistrationClosed e) state.applyRegistrationClosed(e);
    else if (event instanceof TournamentPlayerEnrolled e) state.applyPlayerEnrolled(e);
    else if (event instanceof TournamentStarted e) state.applyTournamentStarted(e);
    else if (event instanceof RebuyProcessed e) state.applyRebuyProcessed(e);
    else if (event instanceof BlindLevelAdvanced e) state.applyBlindAdvanced(e);
    else if (event instanceof PlayerEliminated e) state.applyPlayerEliminated(e);
    else if (event instanceof TournamentPaused e) state.applyPaused(e);
    else if (event instanceof TournamentResumed e) state.applyResumed(e);
    // Rejected events don't change state
  }

  private void enrollPlayer(String name) {
    TournamentPlayerEnrolled event =
        TournamentPlayerEnrolled.newBuilder()
            .setPlayerRoot(playerRoot(name))
            .setFeePaid(state.getBuyIn())
            .setStartingStack(state.getStartingStack())
            .setRegistrationNumber(state.getRegisteredPlayers().size() + 1)
            .build();
    state.applyPlayerEnrolled(event);
  }

  private void createDefaultTournament(String name, int maxPlayers) {
    TournamentCreated event =
        TournamentCreated.newBuilder()
            .setName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(maxPlayers)
            .setMinPlayers(2)
            .build();
    state.applyCreated(event);
  }

  // --- Given steps ---

  @Given("no prior events for the tournament aggregate")
  public void noPriorEvents() {
    state = new TournamentState();
  }

  @Given("a TournamentCreated event for {string}")
  public void tournamentCreated(String name) {
    createDefaultTournament(name, 100);
  }

  @Given("a TournamentCreated event for {string} with max-players {int}")
  public void tournamentCreatedMaxPlayers(String name, int maxPlayers) {
    createDefaultTournament(name, maxPlayers);
  }

  @Given("a RegistrationOpened event")
  public void registrationOpened() {
    state.applyRegistrationOpened(RegistrationOpened.getDefaultInstance());
  }

  @Given("a RegistrationClosed event")
  public void registrationClosed() {
    state.applyRegistrationClosed(
        RegistrationClosed.newBuilder()
            .setTotalRegistrations(state.getRegisteredPlayers().size())
            .build());
  }

  @Given("a TournamentStarted event")
  public void tournamentStarted() {
    state.applyTournamentStarted(
        TournamentStarted.newBuilder()
            .setTotalPlayers(state.getRegisteredPlayers().size())
            .setTotalPrizePool(state.getTotalPrizePool())
            .build());
  }

  @Given("a TournamentPaused event")
  public void tournamentPaused() {
    state.applyPaused(TournamentPaused.newBuilder().setReason("break").build());
  }

  @Given("^(\\d+) players? enrolled$")
  public void nPlayersEnrolled(int n) {
    for (int i = 0; i < n; i++) {
      enrollPlayer("player-" + (i + 1));
    }
  }

  @Given("player {string} enrolled")
  public void playerEnrolled(String name) {
    enrollPlayer(name);
  }

  @Given("player {string} enrolled with {int} rebuys used")
  public void playerEnrolledWithRebuys(String name, int rebuys) {
    enrollPlayer(name);
    for (int i = 0; i < rebuys; i++) {
      state.applyRebuyProcessed(
          RebuyProcessed.newBuilder()
              .setPlayerRoot(playerRoot(name))
              .setRebuyCost(state.getRebuyConfig().getRebuyCost())
              .setChipsAdded(state.getRebuyConfig().getRebuyChips())
              .setRebuyCount(i + 1)
              .build());
    }
  }

  @Given("a running tournament")
  public void runningTournament() {
    createDefaultTournament("Test Tournament", 100);
    registrationOpened();
    nPlayersEnrolled(3);
    registrationClosed();
    tournamentStarted();
  }

  @Given("a running tournament with rebuys enabled max {int} cutoff level {int}")
  public void runningTournamentWithRebuys(int maxRebuys, int cutoffLevel) {
    List<BlindLevel> blinds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      blinds.add(
          BlindLevel.newBuilder()
              .setLevel(i + 1)
              .setSmallBlind((i + 1) * 25L)
              .setBigBlind((i + 1) * 50L)
              .build());
    }
    TournamentCreated event =
        TournamentCreated.newBuilder()
            .setName("Rebuy Tournament")
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .setMinPlayers(2)
            .setRebuyConfig(
                RebuyConfig.newBuilder()
                    .setEnabled(true)
                    .setMaxRebuys(maxRebuys)
                    .setRebuyLevelCutoff(cutoffLevel)
                    .setRebuyCost(1000)
                    .setRebuyChips(10000)
                    .build())
            .addAllBlindStructure(blinds)
            .build();
    state.applyCreated(event);
    registrationOpened();
    nPlayersEnrolled(3);
    registrationClosed();
    tournamentStarted();
  }

  @Given("a running tournament with {int}-level blind structure")
  public void runningTournamentWithBlindStructure(int levels) {
    List<BlindLevel> blinds = new ArrayList<>();
    for (int i = 0; i < levels; i++) {
      blinds.add(
          BlindLevel.newBuilder()
              .setLevel(i + 1)
              .setSmallBlind((i + 1) * 25L)
              .setBigBlind((i + 1) * 50L)
              .build());
    }
    TournamentCreated event =
        TournamentCreated.newBuilder()
            .setName("Blind Tournament")
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setBuyIn(1000)
            .setStartingStack(10000)
            .setMaxPlayers(100)
            .setMinPlayers(2)
            .addAllBlindStructure(blinds)
            .build();
    state.applyCreated(event);
    registrationOpened();
    nPlayersEnrolled(3);
    registrationClosed();
    tournamentStarted();
  }

  @Given("a running tournament with {int} players remaining")
  public void runningTournamentWithPlayers(int n) {
    createDefaultTournament("Elimination Tournament", 100);
    registrationOpened();
    nPlayersEnrolled(n);
    registrationClosed();
    tournamentStarted();
  }

  @Given("the current blind level is {int}")
  public void currentBlindLevel(int level) {
    for (int i = 0; i < level; i++) {
      state.applyBlindAdvanced(
          BlindLevelAdvanced.newBuilder()
              .setLevel(i + 1)
              .setSmallBlind((i + 1) * 25L)
              .setBigBlind((i + 1) * 50L)
              .build());
    }
  }

  @Given("player at position {int} eliminated")
  public void playerAtPositionEliminated(int position) {
    // Find first player and eliminate
    var firstKey = state.getRegisteredPlayers().keySet().iterator().next();
    var reg = state.getRegisteredPlayers().get(firstKey);
    state.applyPlayerEliminated(
        PlayerEliminated.newBuilder()
            .setPlayerRoot(reg.getPlayerRoot())
            .setFinishPosition(state.getPlayersRemaining())
            .build());
  }

  // --- When steps ---

  @When(
      "I handle a CreateTournament command with name {string} buy-in {int} and starting-stack {int}")
  public void handleCreateTournament(String name, int buyIn, int startingStack) {
    CreateTournament cmd =
        CreateTournament.newBuilder()
            .setName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setBuyIn(buyIn)
            .setStartingStack(startingStack)
            .setMaxPlayers(100)
            .setMinPlayers(2)
            .build();
    handleCommand(() -> CreateHandler.handle(cmd, state));
  }

  @When(
      "I handle a CreateTournament command with name {string} buy-in {int} starting-stack {int} and max-players {int}")
  public void handleCreateTournamentMaxPlayers(
      String name, int buyIn, int startingStack, int maxPlayers) {
    CreateTournament cmd =
        CreateTournament.newBuilder()
            .setName(name)
            .setGameVariant(GameVariant.TEXAS_HOLDEM)
            .setBuyIn(buyIn)
            .setStartingStack(startingStack)
            .setMaxPlayers(maxPlayers)
            .setMinPlayers(2)
            .build();
    handleCommand(() -> CreateHandler.handle(cmd, state));
  }

  @When("I handle an OpenRegistration command")
  public void handleOpenRegistration() {
    handleCommand(
        () -> RegistrationHandler.handleOpen(OpenRegistration.getDefaultInstance(), state));
  }

  @When("I handle a CloseRegistration command")
  public void handleCloseRegistration() {
    handleCommand(
        () -> RegistrationHandler.handleClose(CloseRegistration.getDefaultInstance(), state));
  }

  @When("I handle an EnrollPlayer command for player {string}")
  public void handleEnrollPlayer(String name) {
    EnrollPlayer cmd = EnrollPlayer.newBuilder().setPlayerRoot(playerRoot(name)).build();
    handleCommand(() -> RegistrationHandler.handleEnroll(cmd, state));
  }

  @When("I handle a ProcessRebuy command for player {string}")
  public void handleProcessRebuy(String name) {
    ProcessRebuy cmd = ProcessRebuy.newBuilder().setPlayerRoot(playerRoot(name)).build();
    handleCommand(() -> RebuyHandler.handle(cmd, state));
  }

  @When("I handle an AdvanceBlindLevel command")
  public void handleAdvanceBlindLevel() {
    handleCommand(
        () -> LifecycleHandler.handleAdvanceBlind(AdvanceBlindLevel.getDefaultInstance(), state));
  }

  @When("I handle an EliminatePlayer command for player {string}")
  public void handleEliminatePlayer(String name) {
    EliminatePlayer cmd = EliminatePlayer.newBuilder().setPlayerRoot(playerRoot(name)).build();
    handleCommand(() -> LifecycleHandler.handleEliminate(cmd, state));
  }

  @When("I handle a PauseTournament command with reason {string}")
  public void handlePauseTournament(String reason) {
    PauseTournament cmd = PauseTournament.newBuilder().setReason(reason).build();
    handleCommand(() -> LifecycleHandler.handlePause(cmd, state));
  }

  @When("I handle a ResumeTournament command")
  public void handleResumeTournament() {
    handleCommand(
        () -> LifecycleHandler.handleResume(ResumeTournament.getDefaultInstance(), state));
  }

  @When("I rebuild the tournament state")
  public void rebuildTournamentState() {
    // State is already being tracked via applyEvent calls — nothing to do
  }

  // --- Then steps ---

  @Then("^the result is a(?:n)? (?:examples\\.)?TournamentCreated event$")
  public void resultIsTournamentCreated() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(TournamentCreated.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?RegistrationOpened event$")
  public void resultIsRegistrationOpened() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(RegistrationOpened.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?RegistrationClosed event$")
  public void resultIsRegistrationClosed() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(RegistrationClosed.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?TournamentPlayerEnrolled event$")
  public void resultIsPlayerEnrolled() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(TournamentPlayerEnrolled.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?TournamentEnrollmentRejected event$")
  public void resultIsEnrollmentRejected() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(TournamentEnrollmentRejected.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?RebuyProcessed event$")
  public void resultIsRebuyProcessed() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(RebuyProcessed.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?RebuyDenied event$")
  public void resultIsRebuyDenied() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(RebuyDenied.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?BlindLevelAdvanced event$")
  public void resultIsBlindLevelAdvanced() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(BlindLevelAdvanced.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?PlayerEliminated event$")
  public void resultIsPlayerEliminated() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(PlayerEliminated.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?TournamentPaused event$")
  public void resultIsTournamentPaused() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(TournamentPaused.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?TournamentResumed event$")
  public void resultIsTournamentResumed() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(TournamentResumed.class);
  }

  // Field assertions

  @Then("the tournament event has name {string}")
  public void eventHasName(String expected) {
    assertThat(resultEvent).isInstanceOf(TournamentCreated.class);
    assertThat(((TournamentCreated) resultEvent).getName()).isEqualTo(expected);
  }

  @Then("the tournament event has buy_in {int}")
  public void eventHasBuyIn(int expected) {
    assertThat(resultEvent).isInstanceOf(TournamentCreated.class);
    assertThat(((TournamentCreated) resultEvent).getBuyIn()).isEqualTo(expected);
  }

  @Then("the tournament event has starting_stack {int}")
  public void eventHasStartingStack(int expected) {
    if (resultEvent instanceof TournamentCreated e) {
      assertThat(e.getStartingStack()).isEqualTo(expected);
    } else if (resultEvent instanceof TournamentPlayerEnrolled e) {
      assertThat(e.getStartingStack()).isEqualTo(expected);
    } else {
      throw new IllegalStateException("Event does not have starting_stack");
    }
  }

  @Then("the tournament event has total_registrations {int}")
  public void eventHasTotalRegistrations(int expected) {
    assertThat(resultEvent).isInstanceOf(RegistrationClosed.class);
    assertThat(((RegistrationClosed) resultEvent).getTotalRegistrations()).isEqualTo(expected);
  }

  @Then("the tournament event has fee_paid {int}")
  public void eventHasFeePaid(int expected) {
    assertThat(resultEvent).isInstanceOf(TournamentPlayerEnrolled.class);
    assertThat(((TournamentPlayerEnrolled) resultEvent).getFeePaid()).isEqualTo(expected);
  }

  @Then("the tournament event has registration_number {int}")
  public void eventHasRegistrationNumber(int expected) {
    assertThat(resultEvent).isInstanceOf(TournamentPlayerEnrolled.class);
    assertThat(((TournamentPlayerEnrolled) resultEvent).getRegistrationNumber())
        .isEqualTo(expected);
  }

  @Then("the tournament event has reason {string}")
  public void eventHasReason(String expected) {
    if (resultEvent instanceof TournamentEnrollmentRejected e) {
      assertThat(e.getReason()).isEqualTo(expected);
    } else if (resultEvent instanceof RebuyDenied e) {
      assertThat(e.getReason()).isEqualTo(expected);
    } else if (resultEvent instanceof TournamentPaused e) {
      assertThat(e.getReason()).isEqualTo(expected);
    } else {
      throw new IllegalStateException("Event does not have reason field");
    }
  }

  @Then("the tournament event has rebuy_count {int}")
  public void eventHasRebuyCount(int expected) {
    assertThat(resultEvent).isInstanceOf(RebuyProcessed.class);
    assertThat(((RebuyProcessed) resultEvent).getRebuyCount()).isEqualTo(expected);
  }

  @Then("the tournament event has level {int}")
  public void eventHasLevel(int expected) {
    assertThat(resultEvent).isInstanceOf(BlindLevelAdvanced.class);
    assertThat(((BlindLevelAdvanced) resultEvent).getLevel()).isEqualTo(expected);
  }

  @Then("the tournament event has finish_position {int}")
  public void eventHasFinishPosition(int expected) {
    assertThat(resultEvent).isInstanceOf(PlayerEliminated.class);
    assertThat(((PlayerEliminated) resultEvent).getFinishPosition()).isEqualTo(expected);
  }

  // State assertions

  @Then("the tournament state has status {string}")
  public void stateHasStatus(String expected) {
    TournamentStatus expectedStatus =
        switch (expected) {
          case "CREATED" -> TournamentStatus.TOURNAMENT_CREATED;
          case "REGISTRATION_OPEN" -> TournamentStatus.TOURNAMENT_REGISTRATION_OPEN;
          case "RUNNING" -> TournamentStatus.TOURNAMENT_RUNNING;
          case "PAUSED" -> TournamentStatus.TOURNAMENT_PAUSED;
          case "COMPLETED" -> TournamentStatus.TOURNAMENT_COMPLETED;
          default -> throw new IllegalArgumentException("Unknown status: " + expected);
        };
    assertThat(state.getStatus()).isEqualTo(expectedStatus);
  }

  @Then("the tournament state has {int} registered players")
  public void stateHasRegisteredPlayers(int expected) {
    assertThat(state.getRegisteredPlayers()).hasSize(expected);
  }

  @Then("the tournament state has {int} players remaining")
  public void stateHasPlayersRemaining(int expected) {
    assertThat(state.getPlayersRemaining()).isEqualTo(expected);
  }

  @Then("the tournament state has prize pool {int}")
  public void stateHasPrizePool(int expected) {
    assertThat(state.getTotalPrizePool()).isEqualTo(expected);
  }
}
