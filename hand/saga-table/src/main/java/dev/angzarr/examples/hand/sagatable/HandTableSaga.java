package dev.angzarr.examples.hand.sagatable;

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
import dev.angzarr.examples.EndHand;
import dev.angzarr.examples.HandComplete;
import dev.angzarr.examples.PotResult;
import dev.angzarr.examples.PotWinner;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga: Hand → Table — Tier 5 annotation-driven.
 *
 * <p>Reacts to {@link HandComplete} events from the Hand domain and issues an {@link EndHand}
 * command to the Table domain carrying the pot results.
 */
@Saga(name = "saga-hand-table", source = "hand", target = "table")
public class HandTableSaga {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  @Handles(HandComplete.class)
  public SagaHandlerResponse onHandComplete(HandComplete event, Destinations destinations) {
    int destSeq = destinations.sequenceFor("table").orElse(0);

    List<PotResult> results = new ArrayList<>();
    for (PotWinner winner : event.getWinnersList()) {
      results.add(
          PotResult.newBuilder()
              .setWinnerRoot(winner.getPlayerRoot())
              .setAmount(winner.getAmount())
              .setPotType(winner.getPotType())
              .setWinningHand(winner.getWinningHand())
              .build());
    }

    EndHand endHand =
        EndHand.newBuilder().setHandRoot(event.getTableRoot()).addAllResults(results).build();

    CommandBook cmd =
        CommandBook.newBuilder()
            .setCover(
                Cover.newBuilder()
                    .setDomain("table")
                    .setRoot(UUID.newBuilder().setValue(event.getTableRoot())))
            .addPages(
                CommandPage.newBuilder()
                    .setHeader(PageHeader.newBuilder().setSequence(destSeq))
                    .setCommand(Any.pack(endHand, TYPE_URL_PREFIX)))
            .build();

    return SagaHandlerResponse.withCommands(List.of(cmd));
  }
}
