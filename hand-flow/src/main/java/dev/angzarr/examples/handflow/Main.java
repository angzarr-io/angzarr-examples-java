package dev.angzarr.examples.handflow;

import dev.angzarr.client.router.ProcessManagerGrpc;
import dev.angzarr.client.router.ProcessManagerRouter;
import dev.angzarr.client.router.Router;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Hand Flow process manager.
 *
 * <p>Orchestrates poker hand lifecycle by subscribing to table and hand domain events and sending
 * commands to drive hands forward. Because the PM tracks state in an in-memory map, the Router is
 * built with a <em>singleton</em> factory so the same instance is reused across dispatch calls.
 */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class HandFlowPmService extends ProcessManagerGrpc {
    private static final HandFlowProcessManager SHARED = new HandFlowProcessManager();

    public HandFlowPmService() {
      super(
          (ProcessManagerRouter<?>)
              Router.newBuilder("hand-flow-pm")
                  .withHandler(HandFlowProcessManager.class, () -> SHARED)
                  .build());
    }
  }
}
