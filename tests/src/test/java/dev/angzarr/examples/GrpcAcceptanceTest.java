package dev.angzarr.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.CommandRequest;
import dev.angzarr.CommandResponse;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.SyncMode;
import dev.angzarr.client.CommandHandlerClient;
import dev.angzarr.client.Errors;
import dev.angzarr.client.Helpers;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * gRPC acceptance tests for the player aggregate.
 *
 * <p>These tests run against a live deployment (Kind cluster in CI) and verify end-to-end gRPC
 * command handling. They require a running player aggregate coordinator accessible at the address
 * specified by the PLAYER_URL environment variable (default: localhost:1310).
 *
 * <p>Tagged with "grpc-acceptance" so they can be run independently from unit/Cucumber tests.
 */
@Tag("grpc-acceptance")
class GrpcAcceptanceTest {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private static String playerUrl() {
    String url = System.getenv("PLAYER_URL");
    return (url != null && !url.isEmpty()) ? url : "localhost:1310";
  }

  private static CommandRequest makeCommandRequest(
      String domain, UUID root, Message command, int sequence) {
    Any commandAny =
        Any.newBuilder()
            .setTypeUrl(TYPE_URL_PREFIX + command.getDescriptorForType().getFullName())
            .setValue(command.toByteString())
            .build();

    return CommandRequest.newBuilder()
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
                        .setCommand(commandAny)))
        .setSyncMode(SyncMode.SYNC_MODE_SIMPLE)
        .build();
  }

  private static CommandResponse sendPlayerCommand(CommandRequest request) {
    try (CommandHandlerClient client = CommandHandlerClient.connect(playerUrl())) {
      return client.handleCommand(request);
    }
  }

  @Test
  void testConnectivity() {
    String url = playerUrl();
    System.out.printf("Connecting to player aggregate at %s%n", url);

    ManagedChannel channel = ManagedChannelBuilder.forTarget(url).usePlaintext().build();
    try {
      // Force a connection attempt
      channel.getState(true);

      // Wait for the channel to become ready
      long deadline = System.currentTimeMillis() + 10_000;
      while (channel.getState(false) != io.grpc.ConnectivityState.READY) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          fail(
              "Failed to connect to player aggregate at %s: timed out in state %s",
              url, channel.getState(false));
        }
        channel.getState(true);
        Thread.sleep(200);
      }

      System.out.printf("Successfully connected to player aggregate at %s%n", url);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail("Interrupted while connecting to player aggregate at %s", url);
    } finally {
      channel.shutdownNow();
      try {
        channel.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void testRegisterPlayer() {
    UUID playerId = UUID.randomUUID();
    String playerIdHex = playerId.toString().replace("-", "").substring(0, 8);
    System.out.printf("Registering player with ID: %s%n", playerId);

    RegisterPlayer cmd =
        RegisterPlayer.newBuilder()
            .setDisplayName("AcceptanceTestPlayer")
            .setEmail(String.format("test-%s@example.com", playerIdHex))
            .setPlayerType(PlayerType.HUMAN)
            .build();

    CommandRequest request = makeCommandRequest("player", playerId, cmd, 0);
    CommandResponse resp = sendPlayerCommand(request);

    assertThat(resp.hasEvents()).as("Response should contain events").isTrue();
    assertThat(resp.getEvents().hasCover()).as("Event book should have a cover").isTrue();
    assertThat(resp.getEvents().getPagesList()).as("Should have at least one event").isNotEmpty();

    System.out.printf(
        "Successfully registered player, got %d event(s)%n", resp.getEvents().getPagesCount());
  }

  @Test
  void testRegisterAndDeposit() {
    UUID playerId = UUID.randomUUID();
    String playerIdHex = playerId.toString().replace("-", "").substring(0, 8);
    System.out.printf("Test: Register and deposit for player %s%n", playerId);

    // Register a new player
    RegisterPlayer registerCmd =
        RegisterPlayer.newBuilder()
            .setDisplayName("DepositTestPlayer")
            .setEmail(String.format("deposit-%s@example.com", playerIdHex))
            .setPlayerType(PlayerType.HUMAN)
            .build();

    CommandRequest registerRequest = makeCommandRequest("player", playerId, registerCmd, 0);
    sendPlayerCommand(registerRequest);
    System.out.println("Player registered successfully");

    // Deposit funds at sequence 1. Retry with backoff because the aggregate may not have
    // finished processing the registration event yet (eventual consistency).
    DepositFunds depositCmd =
        DepositFunds.newBuilder()
            .setAmount(Currency.newBuilder().setAmount(1000).setCurrencyCode("USD"))
            .build();

    int maxAttempts = 30;
    Exception lastErr = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      CommandRequest depositRequest = makeCommandRequest("player", playerId, depositCmd, 1);
      try {
        CommandResponse resp = sendPlayerCommand(depositRequest);

        assertThat(resp.hasEvents()).as("Response should contain events").isTrue();
        assertThat(resp.getEvents().getPagesList()).as("Should have deposited event").isNotEmpty();

        System.out.printf(
            "Successfully deposited funds, got %d event(s)%n", resp.getEvents().getPagesCount());
        lastErr = null;
        break;
      } catch (Exception e) {
        System.out.printf(
            "DepositFunds attempt %d/%d failed: %s%n", attempt, maxAttempts, e.getMessage());
        lastErr = e;
        try {
          Thread.sleep(500L * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          fail("Interrupted during backoff");
        }
      }
    }
    if (lastErr != null) {
      fail("DepositFunds failed after %d attempts: %s", maxAttempts, lastErr.getMessage());
    }
  }

  @Test
  void testDuplicateRegistrationFails() {
    UUID playerId = UUID.randomUUID();
    System.out.printf("Test: Duplicate registration for player %s%n", playerId);

    RegisterPlayer cmd =
        RegisterPlayer.newBuilder()
            .setDisplayName("DuplicateTestPlayer")
            .setEmail(
                String.format(
                    "dup-%s@example.com", playerId.toString().replace("-", "").substring(0, 8)))
            .setPlayerType(PlayerType.HUMAN)
            .build();

    // First registration should succeed
    CommandRequest request1 = makeCommandRequest("player", playerId, cmd, 0);
    sendPlayerCommand(request1);
    System.out.println("First registration succeeded");

    // Second registration with same ID should fail
    CommandRequest request2 = makeCommandRequest("player", playerId, cmd, 0);
    try {
      sendPlayerCommand(request2);
      fail("Duplicate registration should have failed");
    } catch (Errors.GrpcError e) {
      System.out.printf("Duplicate registration correctly rejected: %s%n", e.getStatusCode());
      assertThat(e.getStatusCode())
          .as("Expected AlreadyExists, FailedPrecondition, or Internal")
          .isIn(
              io.grpc.Status.Code.ALREADY_EXISTS,
              io.grpc.Status.Code.FAILED_PRECONDITION,
              io.grpc.Status.Code.INTERNAL);
    }
  }
}
