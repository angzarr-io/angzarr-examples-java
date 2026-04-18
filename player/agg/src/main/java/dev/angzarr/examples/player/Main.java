package dev.angzarr.examples.player;

import com.google.protobuf.Any;
import dev.angzarr.EventBook;
import dev.angzarr.ReplayRequest;
import dev.angzarr.ReplayResponse;
import dev.angzarr.client.router.CommandHandlerGrpc;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.Router;
import dev.angzarr.examples.Currency;
import dev.angzarr.examples.player.handlers.StateBuilder;
import dev.angzarr.examples.player.state.PlayerState;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Player aggregate.
 *
 * <p>Wires the {@link Player} POJO into a Tier 5 unified {@link Router} and exposes it via {@link
 * CommandHandlerGrpc}. The generated {@code CommandHandlerServiceGrpc.handle} RPC routes through
 * the router; {@code replay} is overridden here to return the rebuilt state as a proto snapshot for
 * conflict detection.
 */
@SpringBootApplication
public class Main {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class PlayerHandlerService extends CommandHandlerGrpc {

    public PlayerHandlerService() {
      super(buildRouter());
    }

    @Override
    public void replay(ReplayRequest request, StreamObserver<ReplayResponse> responseObserver) {
      PlayerState state =
          StateBuilder.fromEventBook(
              EventBook.newBuilder().addAllPages(request.getEventsList()).build());

      dev.angzarr.examples.PlayerState protoState =
          dev.angzarr.examples.PlayerState.newBuilder()
              .setPlayerId(state.getPlayerId())
              .setDisplayName(state.getDisplayName())
              .setEmail(state.getEmail())
              .setPlayerType(dev.angzarr.examples.PlayerType.forNumber(state.getPlayerType()))
              .setAiModelId(state.getAiModelId())
              .setBankroll(
                  Currency.newBuilder().setAmount(state.getBankroll()).setCurrencyCode("CHIPS"))
              .setReservedFunds(
                  Currency.newBuilder()
                      .setAmount(state.getReservedFunds())
                      .setCurrencyCode("CHIPS"))
              .putAllTableReservations(state.getTableReservations())
              .setStatus(state.getStatus())
              .build();

      responseObserver.onNext(
          ReplayResponse.newBuilder().setState(Any.pack(protoState, TYPE_URL_PREFIX)).build());
      responseObserver.onCompleted();
    }

    private static CommandHandlerRouter<PlayerState> buildRouter() {
      @SuppressWarnings("unchecked")
      CommandHandlerRouter<PlayerState> built =
          (CommandHandlerRouter<PlayerState>)
              Router.newBuilder("player-agg").withHandler(Player.class, Player::new).build();
      return built;
    }
  }
}
