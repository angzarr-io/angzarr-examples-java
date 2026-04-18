package dev.angzarr.examples.hand.sagaplayer;

import dev.angzarr.client.router.Router;
import dev.angzarr.client.router.SagaGrpc;
import dev.angzarr.client.router.SagaRouter;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot application for Hand → Player saga. */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class HandPlayerSagaService extends SagaGrpc {
    public HandPlayerSagaService() {
      super(
          (SagaRouter)
              Router.newBuilder("saga-hand-player")
                  .withHandler(HandPlayerSaga.class, HandPlayerSaga::new)
                  .build());
    }
  }
}
