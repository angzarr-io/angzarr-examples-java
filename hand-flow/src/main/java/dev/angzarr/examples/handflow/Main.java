package dev.angzarr.examples.handflow;

import dev.angzarr.*;
import dev.angzarr.client.ProcessManager;
import io.grpc.stub.StreamObserver;
import java.util.List;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Hand Flow process manager.
 *
 * <p>Uses the OO ProcessManager pattern with annotation-based handler registration.
 * Orchestrates poker hand lifecycle by subscribing to table and hand domain events and sending
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

    private final HandFlowProcessManager pm = new HandFlowProcessManager();

    @Override
    public void prepare(
        ProcessManagerPrepareRequest request,
        StreamObserver<ProcessManagerPrepareResponse> responseObserver) {
      List<Cover> destinations =
          pm.prepareDestinations(request.getTrigger(), request.getProcessState());
      responseObserver.onNext(
          ProcessManagerPrepareResponse.newBuilder()
              .addAllDestinations(destinations)
              .build());
      responseObserver.onCompleted();
    }

    @Override
    public void handle(
        ProcessManagerHandleRequest request,
        StreamObserver<ProcessManagerHandleResponse> responseObserver) {
      ProcessManager.DispatchResult result =
          pm.dispatch(request.getTrigger(), request.getProcessState());

      responseObserver.onNext(
          ProcessManagerHandleResponse.newBuilder().addAllCommands(result.getCommands()).build());
      responseObserver.onCompleted();
    }
  }
}
