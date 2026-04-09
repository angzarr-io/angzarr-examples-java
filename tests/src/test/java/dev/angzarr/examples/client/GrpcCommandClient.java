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
 * CommandClient implementation that sends commands via gRPC to per-domain coordinators.
 *
 * <p>Used for acceptance tests against a running deployment. Each domain (player, table, hand) may
 * have its own coordinator endpoint.
 */
public class GrpcCommandClient implements CommandClient {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private final java.util.Map<String, CommandHandlerClient> clients = new java.util.HashMap<>();

  /**
   * Create a client with per-domain routing. playerEndpoint is the default; TABLE_URL and HAND_URL
   * env vars override table and hand endpoints respectively.
   */
  public GrpcCommandClient(String playerEndpoint) {
    String tableEndpoint = System.getenv("TABLE_URL");
    String handEndpoint = System.getenv("HAND_URL");
    if (tableEndpoint == null || tableEndpoint.isEmpty()) {
      tableEndpoint = playerEndpoint;
    }
    if (handEndpoint == null || handEndpoint.isEmpty()) {
      handEndpoint = playerEndpoint;
    }
    clients.put("player", connectWithRetry(playerEndpoint));
    clients.put("table", connectWithRetry(tableEndpoint));
    clients.put("hand", connectWithRetry(handEndpoint));
  }

  private static CommandHandlerClient connectWithRetry(String endpoint) {
    for (int i = 0; i < 10; i++) {
      try {
        return CommandHandlerClient.connect(endpoint);
      } catch (Exception e) {
        if (i == 9) {
          throw e;
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ie);
        }
      }
    }
    throw new RuntimeException("unreachable");
  }

  private CommandHandlerClient clientForDomain(String domain) {
    CommandHandlerClient c = clients.get(domain);
    if (c != null) {
      return c;
    }
    // Fall back to player client for unknown domains
    return clients.get("player");
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
    return clientForDomain(domain).handleCommand(request);
  }

  @Override
  public void close() {
    for (CommandHandlerClient c : clients.values()) {
      c.close();
    }
  }
}
