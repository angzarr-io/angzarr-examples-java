package dev.angzarr.examples.prjoutput;

import dev.angzarr.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Output projector.
 *
 * <p>Subscribes to events from player, table, and hand domains and writes formatted game logs.
 */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class ProjectorGrpcService
      extends ProjectorServiceGrpc.ProjectorServiceImplBase {

    private final OutputProjector projector = new OutputProjector();

    @Override
    public void handle(EventBook request, StreamObserver<Projection> responseObserver) {
      Projection result = projector.handle(request);
      responseObserver.onNext(result);
      responseObserver.onCompleted();
    }

    @Override
    public void handleSpeculative(
        EventBook request, StreamObserver<Projection> responseObserver) {
      // Speculative: same logic but should avoid side effects
      Projection result = projector.handle(request);
      responseObserver.onNext(result);
      responseObserver.onCompleted();
    }
  }
}
