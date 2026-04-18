package dev.angzarr.examples.prjoutput;

import dev.angzarr.client.router.ProjectorGrpc;
import dev.angzarr.client.router.ProjectorRouter;
import dev.angzarr.client.router.Router;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Output projector. Subscribes to events from player, table, and hand
 * domains and writes formatted game logs.
 */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class OutputProjectorService extends ProjectorGrpc {
    public OutputProjectorService() {
      super(
          (ProjectorRouter)
              Router.newBuilder("prj-output")
                  .withHandler(OutputProjector.class, OutputProjector::new)
                  .build());
    }
  }
}
