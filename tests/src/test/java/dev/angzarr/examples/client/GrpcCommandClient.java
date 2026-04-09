package dev.angzarr.examples.client;

import com.google.protobuf.Any;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.CommandRequest;
import dev.angzarr.CommandResponse;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.SyncMode;
import dev.angzarr.client.CommandHandlerClient;
import dev.angzarr.client.Helpers;
import java.util.UUID;

/**
 * CommandClient implementation that sends commands via gRPC to a coordinator.
 *
 * <p>Used for acceptance tests against a running deployment.
 */
public class GrpcCommandClient implements CommandClient {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private final CommandHandlerClient client;

  public GrpcCommandClient(String endpoint) {
    this.client = CommandHandlerClient.connect(endpoint);
  }

  @Override
  public CommandResponse sendCommand(String domain, UUID root, Any command, int sequence) {
    CommandRequest request =
        CommandRequest.newBuilder()
            .setCommand(
                CommandBook.newBuilder()
                    .setCover(
                        Cover.newBuilder()
                            .setDomain(domain)
                            .setRoot(Helpers.uuidToProto(root))
                            .setCorrelationId(UUID.randomUUID().toString()))
                    .addPages(
                        CommandPage.newBuilder()
                            .setHeader(PageHeader.newBuilder().setSequence(sequence))
                            .setCommand(command)))
            .setSyncMode(SyncMode.SYNC_MODE_SIMPLE)
            .build();
    return client.handleCommand(request);
  }

  @Override
  public void close() {
    client.close();
  }
}
