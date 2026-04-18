package dev.angzarr.examples.table.sagahand;

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
import dev.angzarr.examples.DealCards;
import dev.angzarr.examples.HandStarted;
import dev.angzarr.examples.PlayerInHand;
import dev.angzarr.examples.SeatSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga: Table → Hand — Tier 5 annotation-driven.
 *
 * <p>Reacts to {@link HandStarted} events from the Table domain and issues {@link DealCards}
 * commands to the Hand domain, stamping each with the target's next expected sequence.
 */
@Saga(name = "saga-table-hand", source = "table", target = "hand")
public class TableHandSaga {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  @Handles(HandStarted.class)
  public SagaHandlerResponse onHandStarted(HandStarted event, Destinations destinations) {
    int destSeq = destinations.sequenceFor("hand").orElse(0);

    List<PlayerInHand> players = new ArrayList<>();
    for (SeatSnapshot seat : event.getActivePlayersList()) {
      players.add(
          PlayerInHand.newBuilder()
              .setPlayerRoot(seat.getPlayerRoot())
              .setPosition(seat.getPosition())
              .setStack(seat.getStack())
              .build());
    }

    DealCards dealCards =
        DealCards.newBuilder()
            .setTableRoot(event.getHandRoot())
            .setHandNumber(event.getHandNumber())
            .setGameVariant(event.getGameVariant())
            .setDealerPosition(event.getDealerPosition())
            .setSmallBlind(event.getSmallBlind())
            .setBigBlind(event.getBigBlind())
            .addAllPlayers(players)
            .build();

    CommandBook cmd =
        CommandBook.newBuilder()
            .setCover(
                Cover.newBuilder()
                    .setDomain("hand")
                    .setRoot(UUID.newBuilder().setValue(event.getHandRoot())))
            .addPages(
                CommandPage.newBuilder()
                    .setHeader(PageHeader.newBuilder().setSequence(destSeq).build())
                    .setCommand(Any.pack(dealCards, TYPE_URL_PREFIX)))
            .build();

    return SagaHandlerResponse.withCommands(List.of(cmd));
  }
}
