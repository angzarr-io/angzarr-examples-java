package dev.angzarr.examples.handflow;

import dev.angzarr.*;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.stream.Collectors;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Hand Flow process manager.
 *
 * <p>Orchestrates poker hand lifecycle by subscribing to table and hand domain events and sending
 * commands to drive hands forward.
 */
@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @GrpcService
  public static class ProcessManagerGrpcService
      extends ProcessManagerServiceGrpc.ProcessManagerServiceImplBase {

    private final HandFlowProcessManager pm =
        new HandFlowProcessManager(
            cmd -> {
              /* commands returned via gRPC response */
            });

    @Override
    public void prepare(
        ProcessManagerPrepareRequest request,
        StreamObserver<ProcessManagerPrepareResponse> responseObserver) {
      responseObserver.onNext(ProcessManagerPrepareResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void handle(
        ProcessManagerHandleRequest request,
        StreamObserver<ProcessManagerHandleResponse> responseObserver) {
      List<EventBook> destinations =
          request.getDestinationSequencesMap().entrySet().stream()
              .map(e -> e.getValue())
              .collect(Collectors.toList());

      List<CommandBook> commands =
          pm.handle(request.getTrigger(), request.getProcessState(), destinations);

      responseObserver.onNext(
          ProcessManagerHandleResponse.newBuilder().addAllCommands(commands).build());
      responseObserver.onCompleted();
    }
  }
}
