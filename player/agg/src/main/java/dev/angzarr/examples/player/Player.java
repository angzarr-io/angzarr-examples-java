package dev.angzarr.examples.player;

import dev.angzarr.Notification;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Rejected;
import dev.angzarr.examples.DepositFunds;
import dev.angzarr.examples.FundsDeposited;
import dev.angzarr.examples.FundsReleased;
import dev.angzarr.examples.FundsReserved;
import dev.angzarr.examples.FundsTransferred;
import dev.angzarr.examples.FundsWithdrawn;
import dev.angzarr.examples.PlayerRegistered;
import dev.angzarr.examples.RegisterPlayer;
import dev.angzarr.examples.ReleaseFunds;
import dev.angzarr.examples.ReserveFunds;
import dev.angzarr.examples.TransferFunds;
import dev.angzarr.examples.WithdrawFunds;
import dev.angzarr.examples.player.handlers.DepositFundsHandler;
import dev.angzarr.examples.player.handlers.RegisterPlayerHandler;
import dev.angzarr.examples.player.handlers.RejectedHandler;
import dev.angzarr.examples.player.handlers.ReleaseFundsHandler;
import dev.angzarr.examples.player.handlers.ReserveFundsHandler;
import dev.angzarr.examples.player.handlers.TransferFundsHandler;
import dev.angzarr.examples.player.handlers.WithdrawFundsHandler;
import dev.angzarr.examples.player.state.PlayerState;

/**
 * Player aggregate — Tier 5 annotation-driven.
 *
 * <p>Manages player registration, funds, and table reservations. State is rebuilt per dispatch via
 * {@code @Applies} replay; commands are routed by {@code @Handles}. The aggregate delegates
 * business logic to functional handler classes in {@code handlers/} — each is independently
 * unit-testable.
 */
@Aggregate(domain = "player", state = PlayerState.class)
public class Player {

  public static final String DOMAIN = "player";

  // --- Event appliers ---

  @Applies(PlayerRegistered.class)
  public void applyPlayerRegistered(PlayerState state, PlayerRegistered event) {
    state.setPlayerId("player_" + event.getEmail());
    state.setDisplayName(event.getDisplayName());
    state.setEmail(event.getEmail());
    state.setPlayerType(event.getPlayerTypeValue());
    state.setAiModelId(event.getAiModelId());
    state.setStatus("active");
    state.setBankroll(0);
    state.setReservedFunds(0);
  }

  @Applies(FundsDeposited.class)
  public void applyFundsDeposited(PlayerState state, FundsDeposited event) {
    if (event.hasNewBalance()) {
      state.setBankroll(event.getNewBalance().getAmount());
    }
  }

  @Applies(FundsWithdrawn.class)
  public void applyFundsWithdrawn(PlayerState state, FundsWithdrawn event) {
    if (event.hasNewBalance()) {
      state.setBankroll(event.getNewBalance().getAmount());
    }
  }

  @Applies(FundsReserved.class)
  public void applyFundsReserved(PlayerState state, FundsReserved event) {
    if (event.hasNewReservedBalance()) {
      state.setReservedFunds(event.getNewReservedBalance().getAmount());
    }
    String tableKey = bytesToHex(event.getTableRoot().toByteArray());
    if (event.hasAmount()) {
      state.getTableReservations().put(tableKey, event.getAmount().getAmount());
    }
  }

  @Applies(FundsReleased.class)
  public void applyFundsReleased(PlayerState state, FundsReleased event) {
    if (event.hasNewReservedBalance()) {
      state.setReservedFunds(event.getNewReservedBalance().getAmount());
    }
    String tableKey = bytesToHex(event.getTableRoot().toByteArray());
    state.getTableReservations().remove(tableKey);
  }

  @Applies(FundsTransferred.class)
  public void applyFundsTransferred(PlayerState state, FundsTransferred event) {
    if (event.hasNewBalance()) {
      state.setBankroll(event.getNewBalance().getAmount());
    }
  }

  // --- Command handlers ---

  @Handles(RegisterPlayer.class)
  public PlayerRegistered register(RegisterPlayer cmd, PlayerState state, long seq) {
    return RegisterPlayerHandler.handle(cmd, state);
  }

  @Handles(DepositFunds.class)
  public FundsDeposited deposit(DepositFunds cmd, PlayerState state, long seq) {
    return DepositFundsHandler.handle(cmd, state);
  }

  @Handles(WithdrawFunds.class)
  public FundsWithdrawn withdraw(WithdrawFunds cmd, PlayerState state, long seq) {
    return WithdrawFundsHandler.handle(cmd, state);
  }

  @Handles(ReserveFunds.class)
  public FundsReserved reserve(ReserveFunds cmd, PlayerState state, long seq) {
    return ReserveFundsHandler.handle(cmd, state);
  }

  @Handles(ReleaseFunds.class)
  public FundsReleased release(ReleaseFunds cmd, PlayerState state, long seq) {
    return ReleaseFundsHandler.handle(cmd, state);
  }

  @Handles(TransferFunds.class)
  public FundsTransferred transfer(TransferFunds cmd, PlayerState state, long seq) {
    return TransferFundsHandler.handle(cmd, state);
  }

  // --- Rejection handlers ---

  /**
   * Compensate a rejected JoinTable command by releasing the funds we had reserved for that table.
   */
  @Rejected(domain = "table", command = "JoinTable")
  public FundsReleased onJoinTableRejected(Notification notification, PlayerState state) {
    return RejectedHandler.handleJoinRejected(notification, state);
  }

  // --- Helpers ---

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
