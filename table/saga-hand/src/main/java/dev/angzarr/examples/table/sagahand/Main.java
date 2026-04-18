package dev.angzarr.examples.table.sagahand;

import dev.angzarr.client.router.Router;
import dev.angzarr.client.router.SagaGrpc;
import dev.angzarr.client.router.SagaRouter;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot application for Table → Hand saga. */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class TableHandSagaService extends SagaGrpc {
    public TableHandSagaService() {
      super(
          (SagaRouter)
              Router.newBuilder("saga-table-hand")
                  .withHandler(TableHandSaga.class, TableHandSaga::new)
                  .build());
    }
  }
}
