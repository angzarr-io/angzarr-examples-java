package dev.angzarr.examples.client;

import com.google.protobuf.Any;
import dev.angzarr.CommandResponse;
import java.util.UUID;

/**
 * Abstraction for sending commands to angzarr domain aggregates.
 *
 * <p>Two implementations exist:
 *
 * <ul>
 *   <li>{@link InProcessCommandClient} - wraps direct aggregate handler calls (unit tests)
 *   <li>{@link GrpcCommandClient} - sends commands via gRPC to coordinator (acceptance tests)
 * </ul>
 */
public interface CommandClient {

  /**
   * Send a command to a domain aggregate.
   *
   * @param domain The domain name (e.g., "player", "table", "hand")
   * @param root The aggregate root UUID
   * @param command The command packed as protobuf Any
   * @param sequence The expected sequence number for optimistic locking
   * @return The command response containing resulting events
   */
  CommandResponse sendCommand(String domain, UUID root, Any command, int sequence);

  /** Release any resources held by this client. */
  void close();
}
