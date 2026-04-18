package dev.angzarr.examples.hand;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import dev.angzarr.EventBook;
import dev.angzarr.ReplayRequest;
import dev.angzarr.ReplayResponse;
import dev.angzarr.client.router.CommandHandlerGrpc;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.Router;
import dev.angzarr.examples.BettingPhase;
import dev.angzarr.examples.GameVariant;
import dev.angzarr.examples.hand.state.HandState;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot application for Hand aggregate. */
@SpringBootApplication
public class Main {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class HandHandlerService extends CommandHandlerGrpc {

    private final CommandHandlerRouter<HandState> router;

    public HandHandlerService() {
      super(buildRouter());
      @SuppressWarnings("unchecked")
      CommandHandlerRouter<HandState> r = (CommandHandlerRouter<HandState>) getRouter();
      this.router = r;
    }

    @Override
    public void replay(ReplayRequest request, StreamObserver<ReplayResponse> responseObserver) {
      HandState state =
          router.rebuildStateFor(
              Hand.class, EventBook.newBuilder().addAllPages(request.getEventsList()).build());

      dev.angzarr.examples.HandState protoState =
          dev.angzarr.examples.HandState.newBuilder()
              .setHandId(state.getHandId() != null ? state.getHandId() : "")
              .setTableRoot(ByteString.copyFrom(state.getTableRoot()))
              .setHandNumber(state.getHandNumber())
              .setGameVariant(GameVariant.forNumber(state.getGameVariant()))
              .setCurrentPhase(BettingPhase.forNumber(state.getCurrentPhase()))
              .setActionOnPosition(state.getActionOnPosition())
              .setCurrentBet(state.getCurrentBet())
              .setMinRaise(state.getMinRaise())
              .setDealerPosition(state.getDealerPosition())
              .setSmallBlindPosition(state.getSmallBlindPosition())
              .setBigBlindPosition(state.getBigBlindPosition())
              .setStatus(state.getStatus() != null ? state.getStatus() : "")
              .build();

      responseObserver.onNext(
          ReplayResponse.newBuilder().setState(Any.pack(protoState, TYPE_URL_PREFIX)).build());
      responseObserver.onCompleted();
    }

    private static CommandHandlerRouter<HandState> buildRouter() {
      @SuppressWarnings("unchecked")
      CommandHandlerRouter<HandState> built =
          (CommandHandlerRouter<HandState>)
              Router.newBuilder("hand-agg").withHandler(Hand.class, Hand::new).build();
      return built;
    }
  }
}
