package dev.angzarr.examples.tournament.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import dev.angzarr.client.Errors;
import dev.angzarr.examples.*;
import dev.angzarr.examples.tournament.state.TournamentState;
import org.junit.jupiter.api.Test;

class RebuyHandlerTest {

    private TournamentState rebuyState() {
        TournamentState state = new TournamentState();
        state.applyCreated(TournamentCreated.newBuilder()
                .setName("Rebuy").setBuyIn(1000).setStartingStack(10000)
                .setMaxPlayers(100).setMinPlayers(2)
                .setRebuyConfig(RebuyConfig.newBuilder()
                        .setEnabled(true).setMaxRebuys(3).setRebuyLevelCutoff(4)
                        .setRebuyCost(1000).setRebuyChips(10000))
                .build());
        state.applyRegistrationOpened(RegistrationOpened.getDefaultInstance());
        state.applyPlayerEnrolled(TournamentPlayerEnrolled.newBuilder()
                .setPlayerRoot(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .setFeePaid(1000).setStartingStack(10000).build());
        state.applyTournamentStarted(TournamentStarted.getDefaultInstance());
        // Set blind level to 2 (within rebuy window)
        state.applyBlindAdvanced(BlindLevelAdvanced.newBuilder().setLevel(2).build());
        return state;
    }

    @Test
    void rejectsNotRunning() {
        TournamentState state = new TournamentState();
        state.applyCreated(TournamentCreated.newBuilder().setName("Test").setBuyIn(1000)
                .setStartingStack(10000).setMaxPlayers(100).build());

        assertThatThrownBy(() -> RebuyHandler.handle(
                ProcessRebuy.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[]{1})).build(),
                state))
                .isInstanceOf(Errors.CommandRejectedError.class)
                .hasMessageContaining("not running");
    }

    @Test
    void rejectsUnregistered() {
        TournamentState state = rebuyState();

        assertThatThrownBy(() -> RebuyHandler.handle(
                ProcessRebuy.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[]{9, 9, 9})).build(),
                state))
                .isInstanceOf(Errors.CommandRejectedError.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void deniesWindowClosed() {
        TournamentState state = rebuyState();
        state.applyBlindAdvanced(BlindLevelAdvanced.newBuilder().setLevel(5).build()); // Past cutoff

        Message result = RebuyHandler.handle(
                ProcessRebuy.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[]{1, 2, 3})).build(),
                state);

        assertThat(result).isInstanceOf(RebuyDenied.class);
        assertThat(((RebuyDenied) result).getReason()).isEqualTo("window_closed");
    }

    @Test
    void deniesMaxReached() {
        TournamentState state = rebuyState();
        // Apply 3 rebuys to reach max
        for (int i = 0; i < 3; i++) {
            state.applyRebuyProcessed(RebuyProcessed.newBuilder()
                    .setPlayerRoot(ByteString.copyFrom(new byte[]{1, 2, 3}))
                    .setRebuyCount(i + 1).build());
        }

        Message result = RebuyHandler.handle(
                ProcessRebuy.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[]{1, 2, 3})).build(),
                state);

        assertThat(result).isInstanceOf(RebuyDenied.class);
        assertThat(((RebuyDenied) result).getReason()).isEqualTo("max_reached");
    }

    @Test
    void processesSuccessfully() {
        TournamentState state = rebuyState();

        Message result = RebuyHandler.handle(
                ProcessRebuy.newBuilder().setPlayerRoot(ByteString.copyFrom(new byte[]{1, 2, 3})).build(),
                state);

        assertThat(result).isInstanceOf(RebuyProcessed.class);
        RebuyProcessed processed = (RebuyProcessed) result;
        assertThat(processed.getRebuyCost()).isEqualTo(1000);
        assertThat(processed.getChipsAdded()).isEqualTo(10000);
        assertThat(processed.getRebuyCount()).isEqualTo(1);
    }
}
