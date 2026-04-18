package dev.angzarr.examples.table;

import com.google.protobuf.Any;
import dev.angzarr.EventBook;
import dev.angzarr.ReplayRequest;
import dev.angzarr.ReplayResponse;
import dev.angzarr.client.router.CommandHandlerGrpc;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.Router;
import dev.angzarr.examples.table.state.TableState;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot application for Table aggregate. */
@SpringBootApplication
public class Main {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class TableHandlerService extends CommandHandlerGrpc {

    private final CommandHandlerRouter<TableState> router;

    public TableHandlerService() {
      super(buildRouter());
      @SuppressWarnings("unchecked")
      CommandHandlerRouter<TableState> r = (CommandHandlerRouter<TableState>) getRouter();
      this.router = r;
    }

    @Override
    public void replay(ReplayRequest request, StreamObserver<ReplayResponse> responseObserver) {
      TableState state =
          router.rebuildStateFor(
              Table.class, EventBook.newBuilder().addAllPages(request.getEventsList()).build());

      dev.angzarr.examples.TableState protoState =
          dev.angzarr.examples.TableState.newBuilder()
              .setTableId(state.getTableId() != null ? state.getTableId() : "")
              .setTableName(state.getTableName() != null ? state.getTableName() : "")
              .setGameVariant(dev.angzarr.examples.GameVariant.forNumber(state.getGameVariant()))
              .setSmallBlind(state.getSmallBlind())
              .setBigBlind(state.getBigBlind())
              .setMinBuyIn(state.getMinBuyIn())
              .setMaxBuyIn(state.getMaxBuyIn())
              .setMaxPlayers(state.getMaxPlayers())
              .setActionTimeoutSeconds(state.getActionTimeoutSeconds())
              .setStatus(state.getStatus() != null ? state.getStatus() : "")
              .setDealerPosition(state.getDealerPosition())
              .setHandCount(state.getHandCount())
              .build();

      responseObserver.onNext(
          ReplayResponse.newBuilder().setState(Any.pack(protoState, TYPE_URL_PREFIX)).build());
      responseObserver.onCompleted();
    }

    private static CommandHandlerRouter<TableState> buildRouter() {
      @SuppressWarnings("unchecked")
      CommandHandlerRouter<TableState> built =
          (CommandHandlerRouter<TableState>)
              Router.newBuilder("table-agg").withHandler(Table.class, Table::new).build();
      return built;
    }
  }
}
