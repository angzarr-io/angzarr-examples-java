package dev.angzarr.examples.prjoutput;

import dev.angzarr.*;
import dev.angzarr.client.Projection;
import io.grpc.stub.StreamObserver;
import java.util.List;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Output projector.
 *
 * <p>Uses the OO Projector pattern with annotation-based handler registration.
 * Subscribes to events from player, table, and hand domains and writes formatted game logs.
 */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class ProjectorGrpcService extends ProjectorServiceGrpc.ProjectorServiceImplBase {

    private final OutputProjector projector = new OutputProjector();

    @Override
    public void handle(
        EventBook request, StreamObserver<dev.angzarr.Projection> responseObserver) {
      List<Projection> results = projector.project(request);

      // Build protobuf projection from last result
      dev.angzarr.Projection.Builder builder =
          dev.angzarr.Projection.newBuilder()
              .setCover(request.getCover())
              .setProjector("output");

      if (request.getPagesCount() > 0) {
        EventPage lastPage = request.getPages(request.getPagesCount() - 1);
        builder.setSequence(lastPage.getHeader().getSequence());
      }

      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void handleSpeculative(
        EventBook request, StreamObserver<dev.angzarr.Projection> responseObserver) {
      // Speculative: same logic but should avoid side effects
      List<Projection> results = projector.project(request);

      dev.angzarr.Projection.Builder builder =
          dev.angzarr.Projection.newBuilder()
              .setCover(request.getCover())
              .setProjector("output");

      if (request.getPagesCount() > 0) {
        EventPage lastPage = request.getPages(request.getPagesCount() - 1);
        builder.setSequence(lastPage.getHeader().getSequence());
      }

      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }
  }
}
