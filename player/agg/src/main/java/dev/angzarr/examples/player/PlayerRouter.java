// DOC: This file is referenced in docs/docs/components/aggregate.mdx
//      Update documentation when making changes to router patterns.
package dev.angzarr.examples.player;

import dev.angzarr.client.CommandRouter;
import dev.angzarr.examples.*;
import dev.angzarr.examples.player.handlers.*;
import dev.angzarr.examples.player.state.PlayerState;

/**
 * Functional router for Player aggregate.
 *
 * <p>Alternative to the OO annotation-based approach in Player.java. Both patterns produce
 * identical behavior - choose based on team preference.
 */
public final class PlayerRouter {

  private PlayerRouter() {}

  // docs:start:command_router
  public static CommandRouter<PlayerState> create() {
    return new CommandRouter<PlayerState>("player", StateBuilder::fromEventBook)
        .on("RegisterPlayer", RegisterPlayer.class, RegisterPlayerHandler::handle)
        .on("DepositFunds", DepositFunds.class, DepositFundsHandler::handle)
        .on("WithdrawFunds", WithdrawFunds.class, WithdrawFundsHandler::handle)
        .on("ReserveFunds", ReserveFunds.class, ReserveFundsHandler::handle)
        .on("ReleaseFunds", ReleaseFunds.class, ReleaseFundsHandler::handle)
        .on("TransferFunds", TransferFunds.class, TransferFundsHandler::handle);
  }
  // docs:end:command_router
}
