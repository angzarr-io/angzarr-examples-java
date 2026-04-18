package dev.angzarr.examples.player.sagatable;

import dev.angzarr.client.router.Router;
import dev.angzarr.client.router.SagaGrpc;
import dev.angzarr.client.router.SagaRouter;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot application for Player → Table saga. */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class PlayerTableSagaService extends SagaGrpc {
    public PlayerTableSagaService() {
      super(
          (SagaRouter)
              Router.newBuilder("saga-player-table")
                  .withHandler(PlayerTableSaga.class, PlayerTableSaga::new)
                  .build());
    }
  }
}
