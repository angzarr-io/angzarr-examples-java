package dev.angzarr.examples.player.handlers;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.Currency;
import dev.angzarr.examples.FundsTransferred;
import dev.angzarr.examples.TransferFunds;
import dev.angzarr.examples.player.state.PlayerState;
import java.time.Instant;

/** Functional handler for TransferFunds command. */
public final class TransferFundsHandler {

  private TransferFundsHandler() {}

  public static FundsTransferred handle(TransferFunds cmd, PlayerState state) {
    // Guard
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Player does not exist");
    }

    // Validate
    long amount = cmd.hasAmount() ? cmd.getAmount().getAmount() : 0;
    if (amount == 0) {
      throw Errors.CommandRejectedError.invalidArgument("amount must be non-zero");
    }

    // Compute
    long newBalance = state.getBankroll() + amount;
    return FundsTransferred.newBuilder()
        .setFromPlayerRoot(cmd.getFromPlayerRoot())
        .setToPlayerRoot(ByteString.copyFromUtf8(state.getPlayerId()))
        .setAmount(cmd.getAmount())
        .setHandRoot(cmd.getHandRoot())
        .setReason(cmd.getReason())
        .setNewBalance(Currency.newBuilder().setAmount(newBalance).setCurrencyCode("CHIPS"))
        .setTransferredAt(now())
        .build();
  }

  private static Timestamp now() {
    Instant instant = Instant.now();
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
