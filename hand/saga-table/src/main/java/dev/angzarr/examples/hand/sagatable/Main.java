package dev.angzarr.examples.hand.sagatable;

import dev.angzarr.client.router.Router;
import dev.angzarr.client.router.SagaGrpc;
import dev.angzarr.client.router.SagaRouter;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot application for Hand → Table saga. */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class HandTableSagaService extends SagaGrpc {
    public HandTableSagaService() {
      super(
          (SagaRouter)
              Router.newBuilder("saga-hand-table")
                  .withHandler(HandTableSaga.class, HandTableSaga::new)
                  .build());
    }
  }
}
