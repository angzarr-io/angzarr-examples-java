package dev.angzarr.examples.hand.sagaplayer;

import com.google.protobuf.Any;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.UUID;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.router.SagaHandlerResponse;
import dev.angzarr.examples.Currency;
import dev.angzarr.examples.DepositFunds;
import dev.angzarr.examples.PotAwarded;
import dev.angzarr.examples.PotWinner;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga: Hand → Player — Tier 5 annotation-driven.
 *
 * <p>Reacts to {@link PotAwarded} events from the Hand domain and issues {@link DepositFunds}
 * commands to the Player domain, one per winner.
 */
@Saga(name = "saga-hand-player", source = "hand", target = "player")
public class HandPlayerSaga {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  @Handles(PotAwarded.class)
  public SagaHandlerResponse onPotAwarded(PotAwarded event, Destinations destinations) {
    int playerSeq = destinations.sequenceFor("player").orElse(0);
    List<CommandBook> commands = new ArrayList<>();

    for (PotWinner winner : event.getWinnersList()) {
      DepositFunds depositFunds =
          DepositFunds.newBuilder()
              .setAmount(Currency.newBuilder().setAmount(winner.getAmount()))
              .build();
      commands.add(
          CommandBook.newBuilder()
              .setCover(
                  Cover.newBuilder()
                      .setDomain("player")
                      .setRoot(UUID.newBuilder().setValue(winner.getPlayerRoot())))
              .addPages(
                  CommandPage.newBuilder()
                      .setHeader(PageHeader.newBuilder().setSequence(playerSeq))
                      .setCommand(Any.pack(depositFunds, TYPE_URL_PREFIX)))
              .build());
    }

    return SagaHandlerResponse.withCommands(commands);
  }
}
