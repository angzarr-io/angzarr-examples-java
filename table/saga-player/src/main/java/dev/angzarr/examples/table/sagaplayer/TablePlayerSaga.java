package dev.angzarr.examples.table.sagaplayer;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.UUID;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.router.SagaHandlerResponse;
import dev.angzarr.examples.HandEnded;
import dev.angzarr.examples.ReleaseFunds;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga: Table → Player — Tier 5 annotation-driven.
 *
 * <p>Reacts to {@link HandEnded} events from the Table domain and issues {@link ReleaseFunds}
 * commands to the Player domain (one per player who had stack changes this hand).
 */
@Saga(name = "saga-table-player", source = "table", target = "player")
public class TablePlayerSaga {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  @Handles(HandEnded.class)
  public SagaHandlerResponse onHandEnded(HandEnded event, Destinations destinations) {
    int playerSeq = destinations.sequenceFor("player").orElse(0);
    List<CommandBook> commands = new ArrayList<>();

    for (String playerHex : event.getStackChangesMap().keySet()) {
      byte[] playerRoot = hexToBytes(playerHex);
      ReleaseFunds releaseFunds =
          ReleaseFunds.newBuilder().setTableRoot(event.getHandRoot()).build();
      commands.add(
          CommandBook.newBuilder()
              .setCover(
                  Cover.newBuilder()
                      .setDomain("player")
                      .setRoot(UUID.newBuilder().setValue(ByteString.copyFrom(playerRoot))))
              .addPages(
                  CommandPage.newBuilder()
                      .setHeader(PageHeader.newBuilder().setSequence(playerSeq))
                      .setCommand(Any.pack(releaseFunds, TYPE_URL_PREFIX)))
              .build());
    }

    return SagaHandlerResponse.withCommands(commands);
  }

  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte)
              ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }
}
