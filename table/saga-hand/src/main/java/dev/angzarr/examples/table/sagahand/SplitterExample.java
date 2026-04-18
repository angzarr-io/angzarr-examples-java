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
import dev.angzarr.examples.TableSettled;
import dev.angzarr.examples.TransferFunds;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga splitter pattern example — one triggering event produces commands for many aggregates.
 * Referenced by documentation (<code>docs:start:saga_splitter</code>).
 */
// docs:start:saga_splitter
@Saga(name = "example-splitter", source = "table", target = "player")
class SplitterExample {

  @Handles(TableSettled.class)
  public SagaHandlerResponse onTableSettled(TableSettled event, Destinations destinations) {
    List<CommandBook> commands = new ArrayList<>();
    int playerSeq = destinations.sequenceFor("player").orElse(0);
    for (var payout : event.getPayoutsList()) {
      TransferFunds cmd =
          TransferFunds.newBuilder()
              .setTableRoot(event.getTableRoot())
              .setAmount(payout.getAmount())
              .build();
      commands.add(
          CommandBook.newBuilder()
              .setCover(
                  Cover.newBuilder()
                      .setDomain("player")
                      .setRoot(UUID.newBuilder().setValue(payout.getPlayerRoot())))
              .addPages(
                  CommandPage.newBuilder()
                      .setHeader(PageHeader.newBuilder().setSequence(playerSeq))
                      .setCommand(Any.pack(cmd, "type.googleapis.com/")))
              .build());
    }
    return SagaHandlerResponse.withCommands(commands);
  }
}
// docs:end:saga_splitter
