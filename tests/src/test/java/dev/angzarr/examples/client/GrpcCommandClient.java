package dev.angzarr.examples.client;

import com.google.protobuf.Any;
import dev.angzarr.CascadeErrorMode;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandHandlerCoordinatorServiceGrpc;
import dev.angzarr.CommandPage;
import dev.angzarr.CommandRequest;
import dev.angzarr.CommandResponse;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.SyncMode;
import dev.angzarr.UUID;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * CommandClient implementation using raw gRPC channels (not CommandHandlerClient). This avoids
 * eager connection validation that causes ExceptionInInitializerError when the coordinator takes
 * time to become available.
 */
public class GrpcCommandClient implements CommandClient {

  private final Map<String, ManagedChannel> channels = new HashMap<>();
  private final Map<
          String, CommandHandlerCoordinatorServiceGrpc.CommandHandlerCoordinatorServiceBlockingStub>
      stubs = new HashMap<>();

  public GrpcCommandClient(String playerEndpoint) {
    String tableEndpoint = System.getenv("TABLE_URL");
    String handEndpoint = System.getenv("HAND_URL");
    if (tableEndpoint == null || tableEndpoint.isEmpty()) {
      tableEndpoint = playerEndpoint;
    }
    if (handEndpoint == null || handEndpoint.isEmpty()) {
      handEndpoint = playerEndpoint;
    }
    addDomain("player", playerEndpoint);
    addDomain("table", tableEndpoint);
    addDomain("hand", handEndpoint);
  }

  private void addDomain(String domain, String endpoint) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
    channels.put(domain, channel);
    stubs.put(domain, CommandHandlerCoordinatorServiceGrpc.newBlockingStub(channel));
  }

  private CommandHandlerCoordinatorServiceGrpc.CommandHandlerCoordinatorServiceBlockingStub
      stubForDomain(String domain) {
    var stub = stubs.get(domain);
    return stub != null ? stub : stubs.get("player");
  }

  @Override
  public CommandResponse sendCommand(
      String domain, java.util.UUID root, Any command, int sequence) {
    return sendCommand(
        domain, root, command, sequence, SyncMode.SYNC_MODE_SIMPLE, CascadeErrorMode.CASCADE_ERROR_FAIL_FAST);
  }

  @Override
  public CommandResponse sendCommand(
      String domain,
      java.util.UUID root,
      Any command,
      int sequence,
      SyncMode syncMode,
      CascadeErrorMode cascadeErrorMode) {
    byte[] rootBytes = toBytes(root);
    CommandRequest request =
        CommandRequest.newBuilder()
            .setCommand(
                CommandBook.newBuilder()
                    .setCover(
                        Cover.newBuilder()
                            .setDomain(domain)
                            .setRoot(
                                UUID.newBuilder()
                                    .setValue(com.google.protobuf.ByteString.copyFrom(rootBytes)))
                            .setCorrelationId(java.util.UUID.randomUUID().toString()))
                    .addPages(
                        CommandPage.newBuilder()
                            .setHeader(PageHeader.newBuilder().setSequence(sequence))
                            .setCommand(command)))
            .setSyncMode(syncMode)
            .setCascadeErrorMode(cascadeErrorMode)
            .build();
    return stubForDomain(domain).handleCommand(request);
  }

  private static byte[] toBytes(java.util.UUID uuid) {
    long msb = uuid.getMostSignificantBits();
    long lsb = uuid.getLeastSignificantBits();
    byte[] bytes = new byte[16];
    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) (msb >>> (56 - i * 8));
      bytes[i + 8] = (byte) (lsb >>> (56 - i * 8));
    }
    return bytes;
  }

  @Override
  public void close() {
    for (ManagedChannel ch : channels.values()) {
      ch.shutdownNow();
    }
  }
}
