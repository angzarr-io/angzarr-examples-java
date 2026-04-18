package dev.angzarr.examples.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.PageHeader;
import dev.angzarr.client.Errors;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.DispatchException;
import dev.angzarr.client.router.Router;
import dev.angzarr.examples.Currency;
import dev.angzarr.examples.DepositFunds;
import dev.angzarr.examples.FundsDeposited;
import dev.angzarr.examples.FundsReleased;
import dev.angzarr.examples.FundsReserved;
import dev.angzarr.examples.FundsWithdrawn;
import dev.angzarr.examples.PlayerRegistered;
import dev.angzarr.examples.PlayerType;
import dev.angzarr.examples.RegisterPlayer;
import dev.angzarr.examples.ReleaseFunds;
import dev.angzarr.examples.ReserveFunds;
import dev.angzarr.examples.WithdrawFunds;
import dev.angzarr.examples.player.Player;
import dev.angzarr.examples.player.state.PlayerState;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Cucumber step definitions for Player aggregate tests. */
public class PlayerSteps {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private CommandHandlerRouter<PlayerState> router;
  private List<EventPage> eventPages;
  private Message resultEvent;
  private Errors.CommandRejectedError rejectedError;

  @Before
  public void setup() {
    router = buildRouter();
    eventPages = new ArrayList<>();
    resultEvent = null;
    rejectedError = null;
  }

  private static CommandHandlerRouter<PlayerState> buildRouter() {
    @SuppressWarnings("unchecked")
    CommandHandlerRouter<PlayerState> r =
        (CommandHandlerRouter<PlayerState>)
            Router.newBuilder("player-test").withHandler(Player.class, Player::new).build();
    return r;
  }

  private PlayerState state() {
    return router.rebuildStateFor(Player.class, currentEventBook());
  }

  private EventBook currentEventBook() {
    return EventBook.newBuilder()
        .setCover(Cover.newBuilder().setDomain("player"))
        .addAllPages(eventPages)
        .setNextSequence(eventPages.size())
        .build();
  }

  // --- Given steps ---

  @Given("no prior events for the player aggregate")
  public void noPriorEvents() {
    eventPages.clear();
  }

  @Given("a PlayerRegistered event for {string}")
  public void playerRegisteredEventFor(String name) {
    addEvent(
        PlayerRegistered.newBuilder()
            .setDisplayName(name)
            .setEmail(name.toLowerCase() + "@example.com")
            .setPlayerType(PlayerType.HUMAN)
            .build());
  }

  @Given("a FundsDeposited event with amount {int}")
  public void fundsDepositedEventWithAmount(int amount) {
    long currentBankroll = state().getBankroll();
    addEvent(
        FundsDeposited.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount).setCurrencyCode("CHIPS"))
            .setNewBalance(
                Currency.newBuilder().setAmount(currentBankroll + amount).setCurrencyCode("CHIPS"))
            .build());
  }

  @Given("a FundsReserved event with amount {int} for table {string}")
  public void fundsReservedEventWithAmountForTable(int amount, String tableId) {
    PlayerState s = state();
    long newReserved = s.getReservedFunds() + amount;
    long newAvailable = s.getBankroll() - newReserved;
    addEvent(
        FundsReserved.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount).setCurrencyCode("CHIPS"))
            .setTableRoot(ByteString.copyFrom(tableId.getBytes(StandardCharsets.UTF_8)))
            .setNewReservedBalance(
                Currency.newBuilder().setAmount(newReserved).setCurrencyCode("CHIPS"))
            .setNewAvailableBalance(
                Currency.newBuilder().setAmount(newAvailable).setCurrencyCode("CHIPS"))
            .build());
  }

  // --- When steps ---

  @When("I handle a RegisterPlayer command with name {string} and email {string}")
  public void handleRegisterPlayerCommand(String name, String email) {
    dispatch(
        RegisterPlayer.newBuilder()
            .setDisplayName(name)
            .setEmail(email)
            .setPlayerType(PlayerType.HUMAN)
            .build());
  }

  @When("I handle a RegisterPlayer command with name {string} and email {string} as AI")
  public void handleRegisterPlayerCommandAsAI(String name, String email) {
    dispatch(
        RegisterPlayer.newBuilder()
            .setDisplayName(name)
            .setEmail(email)
            .setPlayerType(PlayerType.AI)
            .build());
  }

  @When("I handle a DepositFunds command with amount {int}")
  public void handleDepositFundsCommand(int amount) {
    dispatch(
        DepositFunds.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount).setCurrencyCode("CHIPS"))
            .build());
  }

  @When("I handle a WithdrawFunds command with amount {int}")
  public void handleWithdrawFundsCommand(int amount) {
    dispatch(
        WithdrawFunds.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount).setCurrencyCode("CHIPS"))
            .build());
  }

  @When("I handle a ReserveFunds command with amount {int} for table {string}")
  public void handleReserveFundsCommand(int amount, String tableId) {
    dispatch(
        ReserveFunds.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(amount).setCurrencyCode("CHIPS"))
            .setTableRoot(ByteString.copyFrom(tableId.getBytes(StandardCharsets.UTF_8)))
            .build());
  }

  @When("I handle a ReleaseFunds command for table {string}")
  public void handleReleaseFundsCommand(String tableId) {
    dispatch(
        ReleaseFunds.newBuilder()
            .setTableRoot(ByteString.copyFrom(tableId.getBytes(StandardCharsets.UTF_8)))
            .build());
  }

  @When("I rebuild the player state")
  public void rebuildPlayerState() {
    // No-op: state() always materializes fresh from the router.
  }

  // --- Then steps ---

  @Then("^the result is a(?:n)? (?:examples\\.)?PlayerRegistered event$")
  public void resultIsPlayerRegisteredEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(PlayerRegistered.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?FundsDeposited event$")
  public void resultIsFundsDepositedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(FundsDeposited.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?FundsWithdrawn event$")
  public void resultIsFundsWithdrawnEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(FundsWithdrawn.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?FundsReserved event$")
  public void resultIsFundsReservedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(FundsReserved.class);
  }

  @Then("^the result is a(?:n)? (?:examples\\.)?FundsReleased event$")
  public void resultIsFundsReleasedEvent() {
    assertThat(rejectedError).isNull();
    assertThat(resultEvent).isInstanceOf(FundsReleased.class);
  }

  @Then("the player event has display_name {string}")
  public void playerEventHasDisplayName(String name) {
    assertThat(resultEvent).isInstanceOf(PlayerRegistered.class);
    assertThat(((PlayerRegistered) resultEvent).getDisplayName()).isEqualTo(name);
  }

  @Then("the player event has player_type {string}")
  public void playerEventHasPlayerType(String type) {
    assertThat(resultEvent).isInstanceOf(PlayerRegistered.class);
    assertThat(((PlayerRegistered) resultEvent).getPlayerType())
        .isEqualTo(PlayerType.valueOf(type));
  }

  @Then("the player event has amount {int}")
  public void playerEventHasAmount(int amount) {
    assertThat(getEventAmount()).isEqualTo(amount);
  }

  @Then("the player event has new_balance {int}")
  public void playerEventHasNewBalance(int balance) {
    assertThat(getEventNewBalance()).isEqualTo(balance);
  }

  @Then("the player event has new_available_balance {int}")
  public void playerEventHasNewAvailableBalance(int balance) {
    assertThat(getEventNewAvailableBalance()).isEqualTo(balance);
  }

  @Then("the player state has bankroll {int}")
  public void playerStateHasBankroll(int bankroll) {
    assertThat(state().getBankroll()).isEqualTo(bankroll);
  }

  @Then("the player state has reserved_funds {int}")
  public void playerStateHasReservedFunds(int reserved) {
    assertThat(state().getReservedFunds()).isEqualTo(reserved);
  }

  @Then("the player state has available_balance {int}")
  public void playerStateHasAvailableBalance(int available) {
    assertThat(state().getAvailableBalance()).isEqualTo(available);
  }

  // --- Router plumbing ---

  private void addEvent(Message event) {
    Any any = Any.pack(event, TYPE_URL_PREFIX);
    eventPages.add(
        EventPage.newBuilder()
            .setHeader(PageHeader.newBuilder().setSequence(eventPages.size()))
            .setEvent(any)
            .build());
  }

  private void dispatch(Message command) {
    ContextualCommand ctx =
        ContextualCommand.newBuilder()
            .setCommand(
                CommandBook.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("player"))
                    .addPages(
                        CommandPage.newBuilder().setCommand(Any.pack(command, TYPE_URL_PREFIX))))
            .setEvents(currentEventBook())
            .build();
    try {
      BusinessResponse response = router.dispatch(ctx);
      EventBook emitted = response.getEvents();
      if (emitted.getPagesCount() == 0) {
        resultEvent = null;
      } else {
        for (EventPage page : emitted.getPagesList()) {
          eventPages.add(
              EventPage.newBuilder()
                  .setHeader(PageHeader.newBuilder().setSequence(eventPages.size()))
                  .setEvent(page.getEvent())
                  .build());
        }
        resultEvent = decodeEvent(emitted.getPages(0).getEvent());
      }
      rejectedError = null;
      CommonSteps.setLastRejectedError(null);
    } catch (DispatchException de) {
      resultEvent = null;
      rejectedError = unwrapRejection(de);
      CommonSteps.setLastRejectedError(rejectedError);
    }
  }

  private static Errors.CommandRejectedError unwrapRejection(DispatchException de) {
    for (Throwable t = de; t != null; t = t.getCause()) {
      if (t instanceof Errors.CommandRejectedError cre) {
        return cre;
      }
    }
    return new Errors.CommandRejectedError(de.getMessage(), de.code());
  }

  private static Message decodeEvent(Any any) {
    String typeUrl = any.getTypeUrl();
    String simpleName = typeUrl.substring(typeUrl.lastIndexOf('.') + 1);
    try {
      return switch (simpleName) {
        case "PlayerRegistered" -> PlayerRegistered.parseFrom(any.getValue());
        case "FundsDeposited" -> FundsDeposited.parseFrom(any.getValue());
        case "FundsWithdrawn" -> FundsWithdrawn.parseFrom(any.getValue());
        case "FundsReserved" -> FundsReserved.parseFrom(any.getValue());
        case "FundsReleased" -> FundsReleased.parseFrom(any.getValue());
        case "FundsTransferred" -> dev.angzarr.examples.FundsTransferred.parseFrom(any.getValue());
        default -> throw new IllegalStateException("unknown player event type: " + typeUrl);
      };
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("cannot decode " + typeUrl, e);
    }
  }

  private long getEventAmount() {
    if (resultEvent instanceof FundsDeposited e) return e.getAmount().getAmount();
    if (resultEvent instanceof FundsWithdrawn e) return e.getAmount().getAmount();
    if (resultEvent instanceof FundsReserved e) return e.getAmount().getAmount();
    if (resultEvent instanceof FundsReleased e) return e.getAmount().getAmount();
    throw new IllegalStateException("Event does not have amount: " + resultEvent.getClass());
  }

  private long getEventNewBalance() {
    if (resultEvent instanceof FundsDeposited e) return e.getNewBalance().getAmount();
    if (resultEvent instanceof FundsWithdrawn e) return e.getNewBalance().getAmount();
    throw new IllegalStateException("Event does not have new_balance: " + resultEvent.getClass());
  }

  private long getEventNewAvailableBalance() {
    if (resultEvent instanceof FundsReserved e) return e.getNewAvailableBalance().getAmount();
    if (resultEvent instanceof FundsReleased e) return e.getNewAvailableBalance().getAmount();
    throw new IllegalStateException(
        "Event does not have new_available_balance: " + resultEvent.getClass());
  }
}
