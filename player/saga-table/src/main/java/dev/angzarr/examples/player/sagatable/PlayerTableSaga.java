package dev.angzarr.examples.player.sagatable;

import com.google.protobuf.Any;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.UUID;
import dev.angzarr.client.SagaContext;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.router.SagaHandlerResponse;
import dev.angzarr.examples.PlayerReturningToPlay;
import dev.angzarr.examples.PlayerSatIn;
import dev.angzarr.examples.PlayerSatOut;
import dev.angzarr.examples.PlayerSittingOut;
import java.util.List;

/**
 * Saga: Player → Table — Tier 5 annotation-driven.
 *
 * <p>Propagates player sit-out / sit-in intent as facts to the table domain. Emits:
 *
 * <ul>
 *   <li>{@link PlayerSittingOut} ⇒ {@link PlayerSatOut} fact targeting the relevant table
 *   <li>{@link PlayerReturningToPlay} ⇒ {@link PlayerSatIn} fact targeting the relevant table
 * </ul>
 */
@Saga(name = "saga-player-table", source = "player", target = "table")
public class PlayerTableSaga {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  @Handles(PlayerSittingOut.class)
  public SagaHandlerResponse onPlayerSittingOut(PlayerSittingOut event, SagaContext ctx) {
    PlayerSatOut satOut =
        PlayerSatOut.newBuilder()
            .setPlayerRoot(ctx.sourceRoot())
            .setSatOutAt(event.getSatOutAt())
            .build();
    EventBook fact =
        EventBook.newBuilder()
            .setCover(
                Cover.newBuilder()
                    .setDomain("table")
                    .setRoot(UUID.newBuilder().setValue(event.getTableRoot())))
            .addPages(EventPage.newBuilder().setEvent(Any.pack(satOut, TYPE_URL_PREFIX)))
            .build();
    return SagaHandlerResponse.withEvents(List.of(fact));
  }

  @Handles(PlayerReturningToPlay.class)
  public SagaHandlerResponse onPlayerReturningToPlay(PlayerReturningToPlay event, SagaContext ctx) {
    PlayerSatIn satIn =
        PlayerSatIn.newBuilder()
            .setPlayerRoot(ctx.sourceRoot())
            .setSatInAt(event.getSatInAt())
            .build();
    EventBook fact =
        EventBook.newBuilder()
            .setCover(
                Cover.newBuilder()
                    .setDomain("table")
                    .setRoot(UUID.newBuilder().setValue(event.getTableRoot())))
            .addPages(EventPage.newBuilder().setEvent(Any.pack(satIn, TYPE_URL_PREFIX)))
            .build();
    return SagaHandlerResponse.withEvents(List.of(fact));
  }
}
