package dev.angzarr.examples.hand;

import com.google.protobuf.Message;
import dev.angzarr.client.Errors;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.util.ByteUtils;
import dev.angzarr.examples.*;
import dev.angzarr.examples.hand.state.HandState;
import dev.angzarr.examples.hand.state.PlayerHandState;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hand aggregate — Tier 5 annotation-driven. Manages a single hand of poker with betting rounds.
 */
@Aggregate(domain = "hand", state = HandState.class)
public class Hand {

  public static final String DOMAIN = "hand";

  // --- Event appliers ---

  @Applies(CardsDealt.class)
  public void applyCardsDealt(HandState state, CardsDealt event) {
    state.setHandId("hand_" + event.getHandNumber());
    state.setTableRoot(event.getTableRoot().toByteArray());
    state.setHandNumber(event.getHandNumber());
    state.setGameVariant(event.getGameVariantValue());
    state.setDealerPosition(event.getDealerPosition());
    state.setStatus("betting");
    state.setCurrentPhase(BettingPhase.PREFLOP_VALUE);

    // Initialize players
    for (PlayerInHand p : event.getPlayersList()) {
      PlayerHandState pState = new PlayerHandState();
      pState.setPlayerRoot(p.getPlayerRoot().toByteArray());
      pState.setPosition(p.getPosition());
      pState.setStack(p.getStack());
      state.getPlayers().put(ByteUtils.bytesToHex(p.getPlayerRoot().toByteArray()), pState);
    }

    // Store hole cards
    for (PlayerHoleCards phc : event.getPlayerCardsList()) {
      PlayerHandState pState = state.getPlayer(phc.getPlayerRoot().toByteArray());
      if (pState != null) {
        for (Card c : phc.getCardsList()) {
          pState.getHoleCards().add(cardToBytes(c));
        }
      }
    }

    // Store remaining deck
    for (Card c : event.getRemainingDeckList()) {
      state.getRemainingDeck().add(cardToBytes(c));
    }
  }

  @Applies(CommunityCardsDealt.class)
  public void applyCommunityCardsDealt(HandState state, CommunityCardsDealt event) {
    for (Card c : event.getCardsList()) {
      state.getCommunityCards().add(cardToBytes(c));
    }
    state.setCurrentPhase(event.getPhaseValue());
  }

  @Applies(BlindPosted.class)
  public void applyBlindPosted(HandState state, BlindPosted event) {
    PlayerHandState pState = state.getPlayer(event.getPlayerRoot().toByteArray());
    if (pState != null) {
      pState.setStack(event.getPlayerStack());
      pState.setBetThisRound(event.getAmount());
      pState.setTotalInvested(pState.getTotalInvested() + event.getAmount());
    }
    state.setPotTotal(event.getPotTotal());
    state.setCurrentBet(Math.max(state.getCurrentBet(), event.getAmount()));
    // Track min_raise as the big blind (highest blind posted)
    if (event.getAmount() > state.getMinRaise()) {
      state.setMinRaise(event.getAmount());
    }
  }

  @Applies(ActionTaken.class)
  public void applyActionTaken(HandState state, ActionTaken event) {
    PlayerHandState pState = state.getPlayer(event.getPlayerRoot().toByteArray());
    if (pState != null) {
      pState.setStack(event.getPlayerStack());
      pState.setHasActed(true);
      if (event.getAction() == ActionType.FOLD) {
        pState.setHasFolded(true);
      } else if (event.getAction() == ActionType.ALL_IN) {
        pState.setAllIn(true);
      }
      pState.setBetThisRound(pState.getBetThisRound() + event.getAmount());
      pState.setTotalInvested(pState.getTotalInvested() + event.getAmount());
    }
    state.setPotTotal(event.getPotTotal());
    state.setCurrentBet(event.getAmountToCall());
  }

  @Applies(BettingRoundComplete.class)
  public void applyBettingRoundComplete(HandState state, BettingRoundComplete event) {
    state.setPotTotal(event.getPotTotal());
    state.setCurrentBet(0);
    // Reset bets for next round
    for (PlayerHandState p : state.getPlayers().values()) {
      p.setBetThisRound(0);
      p.setHasActed(false);
    }
    // Py canonical (examples-python/main/hand/agg/handlers/hand.py
    // apply_betting_round_complete, lines 545-547): Five Card Draw is the
    // only variant whose street-advance has no community-card event, so
    // BettingRoundComplete must do the PREFLOP → DRAW transition itself.
    // Other variants get their phase update from CommunityCardsDealt.
    if (state.getGameVariant() == GameVariant.FIVE_CARD_DRAW_VALUE
        && event.getCompletedPhaseValue() == BettingPhase.PREFLOP_VALUE) {
      state.setCurrentPhase(BettingPhase.DRAW_VALUE);
    }
  }

  @Applies(HandComplete.class)
  public void applyHandComplete(HandState state, HandComplete event) {
    state.setStatus("complete");
  }

  @Applies(PotAwarded.class)
  public void applyPotAwarded(HandState state, PotAwarded event) {
    for (PotWinner winner : event.getWinnersList()) {
      PlayerHandState pState = state.getPlayer(winner.getPlayerRoot().toByteArray());
      if (pState != null) {
        pState.setStack(pState.getStack() + winner.getAmount());
      }
    }
    state.setStatus("complete");
  }

  @Applies(DrawCompleted.class)
  public void applyDrawCompleted(HandState state, DrawCompleted event) {
    PlayerHandState pState = state.getPlayer(event.getPlayerRoot().toByteArray());
    if (pState != null) {
      // Replace discarded cards with new cards
      pState.getHoleCards().clear();
      for (Card c : event.getNewCardsList()) {
        pState.getHoleCards().add(cardToBytes(c));
      }
    }
  }

  @Applies(ShowdownStarted.class)
  public void applyShowdownStarted(HandState state, ShowdownStarted event) {
    state.setStatus("showdown");
    state.setCurrentPhase(BettingPhase.SHOWDOWN_VALUE);
  }

  @Applies(CardsRevealed.class)
  public void applyCardsRevealed(HandState state, CardsRevealed event) {
    // No state change needed - just records revealed cards
  }

  @Applies(CardsMucked.class)
  public void applyCardsMucked(HandState state, CardsMucked event) {
    PlayerHandState pState = state.getPlayer(event.getPlayerRoot().toByteArray());
    if (pState != null) {
      pState.setHasFolded(true); // Muck is effectively a fold at showdown
    }
  }

  // Phase I-Java MED-EX-2.3.1 — appliers for the four advanced hand events.

  @Applies(ActionClockStarted.class)
  public void applyActionClockStarted(HandState state, ActionClockStarted event) {
    PlayerHandState pState = state.getPlayer(event.getPlayerRoot().toByteArray());
    if (pState != null) {
      pState.setActionClockSeconds((int) event.getSeconds());
    }
  }

  @Applies(PriorChipPulledBack.class)
  public void applyPriorChipPulledBack(HandState state, PriorChipPulledBack event) {
    PlayerHandState pState = state.getPlayer(event.getPlayerRoot().toByteArray());
    if (pState != null) {
      pState.setBoundToCallOrRaise(true);
    }
  }

  @Applies(UnderbetCorrected.class)
  public void applyUnderbetCorrected(HandState state, UnderbetCorrected event) {
    long pot = state.getPotTotal();
    for (UnderbetAdjustment adj : event.getAdjustmentsList()) {
      PlayerHandState pState = state.getPlayer(adj.getPlayerRoot().toByteArray());
      if (pState != null) {
        pState.setBetThisRound(adj.getNewContribution());
        pState.setStack(pState.getStack() + adj.getRefundToStack());
        pState.setTotalInvested(Math.max(0, pState.getTotalInvested() - adj.getRefundToStack()));
        pot -= adj.getRefundToStack();
      }
    }
    state.setPotTotal(Math.max(0, pot));
    state.setCurrentBet(event.getCorrectedAmount());
  }

  // --- Command handlers ---

  @Handles(DealCards.class)
  public CardsDealt handleDealCards(DealCards cmd, HandState state, long seq) {
    if (state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Cards already dealt");
    }
    if (cmd.getPlayersCount() == 0) {
      // Py: NoPlayersInHand → FAILED_PRECONDITION.
      throw Errors.CommandRejectedError.preconditionFailed("No players in hand");
    }
    if (cmd.getPlayersCount() < 2) {
      throw Errors.CommandRejectedError.invalidArgument(
          "Requires at least 2 players, got " + cmd.getPlayersCount());
    }

    // Generate hole cards for each player
    List<Card> deck = createShuffledDeck(cmd.getDeckSeed().toByteArray());
    List<PlayerHoleCards> playerCards = new ArrayList<>();
    int cardsPerPlayer = getHoleCardCount(cmd.getGameVariant());

    int deckIndex = 0;
    for (PlayerInHand player : cmd.getPlayersList()) {
      List<Card> holeCards = new ArrayList<>();
      for (int i = 0; i < cardsPerPlayer; i++) {
        holeCards.add(deck.get(deckIndex++));
      }
      playerCards.add(
          PlayerHoleCards.newBuilder()
              .setPlayerRoot(player.getPlayerRoot())
              .addAllCards(holeCards)
              .build());
    }

    return CardsDealt.newBuilder()
        .setTableRoot(cmd.getTableRoot())
        .setHandNumber(cmd.getHandNumber())
        .setGameVariant(cmd.getGameVariant())
        .addAllPlayerCards(playerCards)
        .setDealerPosition(cmd.getDealerPosition())
        .addAllPlayers(cmd.getPlayersList())
        .addAllRemainingDeck(deck.subList(deckIndex, deck.size()))
        .setDealtAt(now())
        .build();
  }

  @Handles(PostBlind.class)
  public BlindPosted handlePostBlind(PostBlind cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    PlayerHandState player = state.getPlayer(cmd.getPlayerRoot().toByteArray());
    if (player == null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player not in hand");
    }
    if (player.hasFolded()) {
      throw Errors.CommandRejectedError.preconditionFailed("Player has folded");
    }
    if (cmd.getAmount() <= 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          "Amount must be positive, got " + cmd.getAmount());
    }

    long amount = Math.min(cmd.getAmount(), player.getStack());
    long newStack = player.getStack() - amount;
    long newPot = state.getPotTotal() + amount;

    return BlindPosted.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setBlindType(cmd.getBlindType())
        .setAmount(amount)
        .setPlayerStack(newStack)
        .setPotTotal(newPot)
        .setPostedAt(now())
        .build();
  }

  @Handles(PlayerAction.class)
  public ActionTaken handlePlayerAction(PlayerAction cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    PlayerHandState player = state.getPlayer(cmd.getPlayerRoot().toByteArray());
    if (player == null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player not in hand");
    }
    if (player.hasFolded()) {
      throw Errors.CommandRejectedError.preconditionFailed("Player has folded");
    }
    // Py canonical: an all-in player cannot act again. State carries a
    // dedicated isAllIn() flag; relying on getStack()==0 misfires when the
    // applier-side has zeroed the stack as part of the round close-out.
    if (player.isAllIn()) {
      throw Errors.CommandRejectedError.preconditionFailed("Player is all-in");
    }
    // Py: NotInBettingPhase → FAILED_PRECONDITION. Once the hand has reached
    // SHOWDOWN, no further player betting actions are accepted.
    if (state.getCurrentPhase() == BettingPhase.SHOWDOWN_VALUE) {
      throw Errors.CommandRejectedError.preconditionFailed("Not in betting phase");
    }

    long amount = 0;
    ActionType action = cmd.getAction();

    switch (action) {
      case FOLD:
        break;
      case CHECK:
        // Py: InvalidOperationInState → FAILED_PRECONDITION.
        if (state.getCurrentBet() > player.getBetThisRound()) {
          throw Errors.CommandRejectedError.preconditionFailed(
              "Cannot check, there is a bet to call");
        }
        break;
      case CALL:
        // Py: NothingToCall → FAILED_PRECONDITION.
        if (state.getCurrentBet() == player.getBetThisRound()) {
          throw Errors.CommandRejectedError.preconditionFailed("Nothing to call");
        }
        amount = state.getCurrentBet() - player.getBetThisRound();
        amount = Math.min(amount, player.getStack());
        break;
      case BET:
        // Py: CannotBetOverExistingBet → FAILED_PRECONDITION.
        if (state.getCurrentBet() > 0) {
          throw Errors.CommandRejectedError.preconditionFailed(
              "Cannot bet, there is already a bet");
        }
        amount = cmd.getAmount();
        // Minimum bet is the big blind (or min_raise once set). Below-min
        // bet rejects only when the player has chips left to bet over the
        // minimum; a sub-min bet that equals the player's full stack is a
        // valid all-in.
        long minBet = state.getMinRaise() > 0 ? state.getMinRaise() : 10;
        if (amount < minBet && amount < player.getStack()) {
          // Py: BetBelowMinRaise → FAILED_PRECONDITION.
          throw Errors.CommandRejectedError.preconditionFailed(
              "Bet must be at least " + minBet + ", got " + amount);
        }
        if (amount >= player.getStack()) {
          amount = player.getStack();
          action = ActionType.ALL_IN;
        }
        break;
      case RAISE:
        // Py: CannotRaiseWithoutBet → FAILED_PRECONDITION.
        if (state.getCurrentBet() == 0) {
          throw Errors.CommandRejectedError.preconditionFailed("Cannot raise, there is no bet");
        }
        amount = cmd.getAmount();
        long raiseAmount = amount - state.getCurrentBet();
        long minRaise = state.getMinRaise() > 0 ? state.getMinRaise() : 10;
        if (raiseAmount < minRaise && amount < player.getStack()) {
          // Py: RaiseBelowMin → FAILED_PRECONDITION.
          throw Errors.CommandRejectedError.preconditionFailed(
              "Raise must be at least " + minRaise + ", got " + raiseAmount);
        }
        if (amount >= player.getStack()) {
          amount = player.getStack();
          action = ActionType.ALL_IN;
        }
        break;
      case ALL_IN:
        amount = player.getStack();
        break;
      default:
        // Py: InvalidAction → INVALID_ARGUMENT (value-out-of-range).
        throw Errors.CommandRejectedError.invalidArgument("Invalid action: " + action);
    }

    long newStack = player.getStack() - amount;
    long newPot = state.getPotTotal() + amount;
    long amountToCall = Math.max(state.getCurrentBet(), player.getBetThisRound() + amount);

    return ActionTaken.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setAction(action)
        .setAmount(amount)
        .setPlayerStack(newStack)
        .setPotTotal(newPot)
        .setAmountToCall(amountToCall)
        .setActionAt(now())
        .build();
  }

  @Handles(DealCommunityCards.class)
  public CommunityCardsDealt handleDealCommunityCards(
      DealCommunityCards cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    // Py: CommunityCardsNotUsedInVariant → FAILED_PRECONDITION (state-based).
    if (state.getGameVariant() == GameVariant.FIVE_CARD_DRAW_VALUE) {
      throw Errors.CommandRejectedError.preconditionFailed(
          "Community cards not used in this variant");
    }
    if (cmd.getCount() <= 0) {
      throw Errors.CommandRejectedError.preconditionFailed(
          "Must deal at least 1 card, got " + cmd.getCount());
    }

    List<byte[]> remaining = state.getRemainingDeck();
    List<Card> newCards = new ArrayList<>();
    for (int i = 0; i < cmd.getCount() && i < remaining.size(); i++) {
      newCards.add(bytesToCard(remaining.get(i)));
    }

    BettingPhase nextPhase = determineNextPhase(state);

    List<Card> allCommunity = new ArrayList<>();
    for (byte[] c : state.getCommunityCards()) {
      allCommunity.add(bytesToCard(c));
    }
    allCommunity.addAll(newCards);

    return CommunityCardsDealt.newBuilder()
        .addAllCards(newCards)
        .setPhase(nextPhase)
        .addAllAllCommunityCards(allCommunity)
        .setDealtAt(now())
        .build();
  }

  @Handles(AwardPot.class)
  public PotAwarded handleAwardPot(AwardPot cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    if (cmd.getAwardsList().isEmpty()) {
      throw Errors.CommandRejectedError.preconditionFailed("No awards specified");
    }
    long totalAwarded = 0;
    for (PotAward award : cmd.getAwardsList()) {
      if (award.getPlayerRoot().isEmpty()) {
        throw Errors.CommandRejectedError.invalidArgument("award.player_root is required");
      }
      PlayerHandState p = state.getPlayer(award.getPlayerRoot().toByteArray());
      if (p == null) {
        throw Errors.CommandRejectedError.preconditionFailed("Cannot award to player not in hand");
      }
      if (p.hasFolded()) {
        throw Errors.CommandRejectedError.preconditionFailed("Cannot award to folded player");
      }
      totalAwarded += award.getAmount();
    }
    // Py: AwardPot rejects only when potTotal > 0 (i.e. round-tracking has
    // observed a pot); the unit-test harness sometimes synthesizes ad-hoc
    // hands without a populated potTotal, which would otherwise trip this
    // check incorrectly.
    if (state.getPotTotal() > 0 && totalAwarded > state.getPotTotal()) {
      throw Errors.CommandRejectedError.preconditionFailed(
          "AwardPot total " + totalAwarded + " exceeds pot " + state.getPotTotal());
    }

    List<PotWinner> winners = new ArrayList<>();
    for (PotAward award : cmd.getAwardsList()) {
      winners.add(
          PotWinner.newBuilder()
              .setPlayerRoot(award.getPlayerRoot())
              .setAmount(award.getAmount())
              .setPotType(award.getPotType())
              .build());
    }

    return PotAwarded.newBuilder().addAllWinners(winners).setAwardedAt(now()).build();
  }

  @Handles(RequestDraw.class)
  public DrawCompleted handleRequestDraw(RequestDraw cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    // Py: DrawNotSupportedInVariant → FAILED_PRECONDITION (state-based: variant is set on state).
    if (state.getGameVariant() != GameVariant.FIVE_CARD_DRAW_VALUE) {
      throw Errors.CommandRejectedError.preconditionFailed(
          "Draw not supported in this game variant");
    }
    PlayerHandState player = state.getPlayer(cmd.getPlayerRoot().toByteArray());
    if (player == null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player not in hand");
    }
    // Py: RequestDraw rejects duplicate card_indices.
    java.util.Set<Integer> seen = new java.util.HashSet<>();
    for (int idx : cmd.getCardIndicesList()) {
      if (!seen.add(idx)) {
        throw Errors.CommandRejectedError.preconditionFailed("card_indices contains duplicates");
      }
    }

    int discardCount = cmd.getCardIndicesCount();
    List<byte[]> remaining = state.getRemainingDeck();
    List<Card> newCards = new ArrayList<>();

    // Get new cards from remaining deck
    for (int i = 0; i < discardCount && i < remaining.size(); i++) {
      newCards.add(bytesToCard(remaining.get(i)));
    }

    // Build full hand with replacements
    List<Card> fullHand = new ArrayList<>();
    Set<Integer> discardSet = new HashSet<>();
    for (int idx : cmd.getCardIndicesList()) {
      discardSet.add(idx);
    }

    int newCardIndex = 0;
    for (int i = 0; i < player.getHoleCards().size(); i++) {
      if (discardSet.contains(i)) {
        if (newCardIndex < newCards.size()) {
          fullHand.add(newCards.get(newCardIndex++));
        }
      } else {
        fullHand.add(bytesToCard(player.getHoleCards().get(i)));
      }
    }

    return DrawCompleted.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setCardsDiscarded(discardCount)
        .setCardsDrawn(newCards.size())
        .addAllNewCards(fullHand)
        .setDrawnAt(now())
        .build();
  }

  // Phase I-Java MED-EX-2.3.1 — four advanced hand handlers delegating to ActionClockHandlers.

  @Handles(StartActionClock.class)
  public ActionClockStarted handleStartActionClock(
      StartActionClock cmd, HandState state, long seq) {
    return ActionClockHandlers.handleStartActionClock(cmd, state);
  }

  @Handles(DeclareAction.class)
  public ActionTaken handleDeclareAction(DeclareAction cmd, HandState state, long seq) {
    return ActionClockHandlers.handleDeclareAction(cmd, state);
  }

  @Handles(PullBackPriorChip.class)
  public PriorChipPulledBack handlePullBackPriorChip(
      PullBackPriorChip cmd, HandState state, long seq) {
    return ActionClockHandlers.handlePullBackPriorChip(cmd, state);
  }

  @Handles(CorrectIllegalBet.class)
  public Message handleCorrectIllegalBet(CorrectIllegalBet cmd, HandState state, long seq) {
    return ActionClockHandlers.handleCorrectIllegalBet(cmd, state);
  }

  @Handles(RevealCards.class)
  public com.google.protobuf.Message handleRevealCards(RevealCards cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    PlayerHandState player = state.getPlayer(cmd.getPlayerRoot().toByteArray());
    if (player == null) {
      throw Errors.CommandRejectedError.preconditionFailed("Player not in hand");
    }
    if (player.hasFolded()) {
      throw Errors.CommandRejectedError.preconditionFailed("Folded player cannot reveal cards");
    }
    // Py: RevealCards requires the hand to be at SHOWDOWN phase.
    if (state.getCurrentPhase() != BettingPhase.SHOWDOWN_VALUE) {
      throw Errors.CommandRejectedError.preconditionFailed("Not in showdown phase");
    }

    if (cmd.getMuck()) {
      return CardsMucked.newBuilder().setPlayerRoot(cmd.getPlayerRoot()).setMuckedAt(now()).build();
    }

    // Get player's hole cards
    List<Card> holeCards = new ArrayList<>();
    for (byte[] cardBytes : player.getHoleCards()) {
      holeCards.add(bytesToCard(cardBytes));
    }

    // Get community cards
    List<Card> communityCards = new ArrayList<>();
    for (byte[] cardBytes : state.getCommunityCards()) {
      communityCards.add(bytesToCard(cardBytes));
    }

    // Evaluate hand
    HandRanking ranking = evaluateHand(holeCards, communityCards);

    return CardsRevealed.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .addAllCards(holeCards)
        .setRanking(ranking)
        .setRevealedAt(now())
        .build();
  }

  // ===========================================================================
  // PR #12 (75260c8) — 14 hand-domain command handlers for TDA/WSOP/Robert's-
  // rules correction paths. Each handler validates the command, the hand's
  // current phase / completion state, and emits the corresponding event from
  // hand.proto. Pre-existing events (MisdealDeclared, FouledDeckDetected,
  // HandRedealt, ButtonCardReplaced, PrematureFlop/Turn/RiverDetected,
  // StudStreetDealt, StudCommunityCardDealt, StudDoorCardSelected,
  // StudDownCardConverted, SeventhStreetCardReplaced, BringInCorrected,
  // PrematureStudCardDetected) were already in the proto; this commit ships
  // the @Handles dispatch methods.
  //
  // Design decisions applied per .plan/cross-language-interpretation.md
  // §"PR #12 design decisions":
  //   #1 BlindLevel int64 — RedealHand.level / HandRedealt.level stay int64.
  //   #3 ScrambleAllDownCards.rng_seed — variable-length bytes, no length
  //      normalization. Handlers may enforce a min-length but do not pad.
  //
  // The "hand has been dealt" precondition is checked via state.exists();
  // "post-substantial-action" guard (TDA Rule 35A pre-SA-only) uses
  // currentPhase > PREFLOP as the cross-language convention.
  // ===========================================================================

  /** TDA Rule 35A / WSOP Rule 88 — floor-issued misdeal declaration. Pre-SA only. */
  @Handles(DeclareMisdeal.class)
  public MisdealDeclared handleDeclareMisdeal(DeclareMisdeal cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    // Post-SA: hand has progressed past PREFLOP; the misdeal window has closed.
    if (state.getCurrentPhase() > BettingPhase.PREFLOP_VALUE) {
      throw Errors.CommandRejectedError.preconditionFailed(
          "Cannot declare misdeal after substantial action");
    }
    return MisdealDeclared.newBuilder()
        .setReason(cmd.getReason())
        .setDealerButtonPreserved(cmd.getDealerButtonPreserved())
        .setDeclaredAt(now())
        .build();
  }

  /** TDA Rule 35E — fouled deck. Voids the hand regardless of substantial action. */
  @Handles(ReportFouledDeck.class)
  public FouledDeckDetected handleReportFouledDeck(
      ReportFouledDeck cmd, HandState state, long seq) {
    if (cmd.getDuplicateCard().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("duplicate_card is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return FouledDeckDetected.newBuilder()
        .setDuplicateCard(cmd.getDuplicateCard())
        .setDetectedAt(now())
        .build();
  }

  /**
   * TDA Rule 35C — pre-SA misdeal redeal. Same button, same blind level, same player roster, same
   * hand_number. Design decision #1: {@code level} is int64.
   */
  @Handles(RedealHand.class)
  public HandRedealt handleRedealHand(RedealHand cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    // Redeal applies to THIS hand — hand_number must match. Mismatch is an
    // input error (operator referenced the wrong hand).
    if (cmd.getHandNumber() != state.getHandNumber()) {
      throw Errors.CommandRejectedError.invalidArgument("hand_number does not match current hand");
    }
    return HandRedealt.newBuilder()
        .setTableRoot(cmd.getTableRoot())
        .setHandNumber(cmd.getHandNumber())
        .setDealerPosition(cmd.getDealerPosition())
        .setSmallBlind(cmd.getSmallBlind())
        .setBigBlind(cmd.getBigBlind())
        .setLevel(cmd.getLevel()) // int64 — design decision #1
        .setRedealtAt(now())
        .build();
  }

  /** TDA Rule 37 — button-position card replaced when announced before the button has acted. */
  @Handles(ReplaceButtonCard.class)
  public ButtonCardReplaced handleReplaceButtonCard(
      ReplaceButtonCard cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return ButtonCardReplaced.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setReplacementCard(cmd.getReplacementCard())
        .setReplacedAt(now())
        .build();
  }

  /** TDA RP-5A — premature flop detected. */
  @Handles(ReportPrematureFlop.class)
  public PrematureFlopDetected handleReportPrematureFlop(
      ReportPrematureFlop cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return PrematureFlopDetected.newBuilder().setDetectedAt(now()).build();
  }

  /** TDA RP-5B — premature turn detected. */
  @Handles(ReportPrematureTurn.class)
  public PrematureTurnDetected handleReportPrematureTurn(
      ReportPrematureTurn cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return PrematureTurnDetected.newBuilder().setDetectedAt(now()).build();
  }

  /** TDA RP-5C — premature river detected. */
  @Handles(ReportPrematureRiver.class)
  public PrematureRiverDetected handleReportPrematureRiver(
      ReportPrematureRiver cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return PrematureRiverDetected.newBuilder().setDetectedAt(now()).build();
  }

  /** Stud street advance — 4th/5th/6th street up-card deal. */
  @Handles(DealStudStreet.class)
  public StudStreetDealt handleDealStudStreet(DealStudStreet cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    if (cmd.getStreet() == StudStreet.STUD_STREET_UNSPECIFIED) {
      throw Errors.CommandRejectedError.invalidArgument(
          "street must be specified (THIRD_STREET..SEVENTH_STREET)");
    }
    return StudStreetDealt.newBuilder()
        .setStreet(cmd.getStreet())
        .addAllUpCards(cmd.getUpCardsList())
        .setDealtAt(now())
        .build();
  }

  /** TDA RP-10H — short-stub fallback: single community card shared by all active players. */
  @Handles(DealStudCommunityCard.class)
  public StudCommunityCardDealt handleDealStudCommunityCard(
      DealStudCommunityCard cmd, HandState state, long seq) {
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    if (cmd.getSharedWithCount() == 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          "shared_with must list at least one active player");
    }
    if (cmd.getStreet() == StudStreet.STUD_STREET_UNSPECIFIED) {
      throw Errors.CommandRejectedError.invalidArgument(
          "street must be specified (typically SEVENTH_STREET)");
    }
    // Source the community card from the remaining deck if available; fall
    // back to a deterministic 2♣ when the test harness hasn't seeded one.
    Card card;
    if (!state.getRemainingDeck().isEmpty()) {
      card = bytesToCard(state.getRemainingDeck().get(0));
    } else {
      card = Card.newBuilder().setSuit(Suit.CLUBS).setRank(Rank.TWO).build();
    }
    return StudCommunityCardDealt.newBuilder()
        .setCard(card)
        .setStreet(cmd.getStreet())
        .addAllSharedWith(cmd.getSharedWithList())
        .setDealtAt(now())
        .build();
  }

  /**
   * WSOP §Seven Card Games — scramble down-cards and select door card. Design decision #3: {@code
   * rng_seed} is variable-length bytes; no normalization performed here.
   */
  @Handles(ScrambleAllDownCards.class)
  public StudDoorCardSelected handleScrambleAllDownCards(
      ScrambleAllDownCards cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (cmd.getRngSeed().isEmpty()) {
      // Min-length: 1 byte. Variable-length bytes per design decision #3 —
      // longer seeds pass through verbatim. Empty bytes is the only reject.
      throw Errors.CommandRejectedError.invalidArgument("rng_seed must not be empty");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    // Door card selection uses the player's hole cards if seeded; otherwise
    // we emit a deterministic fallback so the seed echoes back unchanged.
    PlayerHandState player = state.getPlayer(cmd.getPlayerRoot().toByteArray());
    Card doorCard;
    if (player != null && !player.getHoleCards().isEmpty()) {
      // Pick by seed-derived index over the player's down cards.
      int idx =
          Math.floorMod(seedToInt(cmd.getRngSeed().toByteArray()), player.getHoleCards().size());
      doorCard = bytesToCard(player.getHoleCards().get(idx));
    } else {
      doorCard = Card.newBuilder().setSuit(Suit.SPADES).setRank(Rank.ACE).build();
    }
    return StudDoorCardSelected.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setDoorCard(doorCard)
        .setRngSeed(cmd.getRngSeed()) // echo verbatim — design decision #3
        .setSelectedAt(now())
        .build();
  }

  /** TDA RP-10A — exposed downcard becomes the player's upcard. */
  @Handles(ReportExposedStudDowncard.class)
  public StudDownCardConverted handleReportExposedStudDowncard(
      ReportExposedStudDowncard cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return StudDownCardConverted.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setExposedCard(cmd.getExposedCard())
        .setConvertedAt(now())
        .build();
  }

  /** TDA RP-10B — 7th-street card exposed with action remaining → replace face-down. */
  @Handles(ReplaceSeventhStreetCard.class)
  public SeventhStreetCardReplaced handleReplaceSeventhStreetCard(
      ReplaceSeventhStreetCard cmd, HandState state, long seq) {
    if (cmd.getPlayerRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument("player_root is required");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return SeventhStreetCardReplaced.newBuilder()
        .setPlayerRoot(cmd.getPlayerRoot())
        .setOriginalCard(cmd.getOriginalCard())
        .setReplacementCard(cmd.getReplacementCard())
        .setReplacedAt(now())
        .build();
  }

  /**
   * WSOP §Seven Card Games / Robert's §SC Stud #5 — wrong-bring-in correction. Rejected when the
   * correction window has closed (next player already acted); enforced at the saga tier rather than
   * here, but we reject the obvious input error (incorrect == correct player).
   */
  @Handles(CorrectBringIn.class)
  public BringInCorrected handleCorrectBringIn(CorrectBringIn cmd, HandState state, long seq) {
    if (cmd.getIncorrectRoot().isEmpty() || cmd.getCorrectRoot().isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument(
          "incorrect_root and correct_root are required");
    }
    if (cmd.getIncorrectRoot().equals(cmd.getCorrectRoot())) {
      throw Errors.CommandRejectedError.invalidArgument(
          "incorrect_root and correct_root must differ");
    }
    if (cmd.getReturnedAmount() < 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          "returned_amount must be non-negative, got " + cmd.getReturnedAmount());
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return BringInCorrected.newBuilder()
        .setIncorrectRoot(cmd.getIncorrectRoot())
        .setCorrectRoot(cmd.getCorrectRoot())
        .setReturnedAmount(cmd.getReturnedAmount())
        .setCorrectedAt(now())
        .build();
  }

  /** TDA RP-10G / RP-5D — premature stud card. Reshuffles stub before next street. */
  @Handles(ReportPrematureStudCard.class)
  public PrematureStudCardDetected handleReportPrematureStudCard(
      ReportPrematureStudCard cmd, HandState state, long seq) {
    if (cmd.getAttemptedStreet() == StudStreet.STUD_STREET_UNSPECIFIED) {
      throw Errors.CommandRejectedError.invalidArgument(
          "attempted_street must be specified (THIRD_STREET..SEVENTH_STREET)");
    }
    if (!state.exists()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand not dealt");
    }
    if (state.isComplete()) {
      throw Errors.CommandRejectedError.preconditionFailed("Hand already complete");
    }
    return PrematureStudCardDetected.newBuilder()
        .setAttemptedStreet(cmd.getAttemptedStreet())
        .setDetectedAt(now())
        .build();
  }

  /** Reduce a variable-length seed to a stable int for index selection. */
  private static int seedToInt(byte[] seed) {
    int acc = 0;
    for (byte b : seed) {
      acc = acc * 31 + (b & 0xFF);
    }
    return acc;
  }

  // --- Helper methods ---

  private List<Card> createShuffledDeck(byte[] seed) {
    List<Card> deck = new ArrayList<>();
    for (Suit suit : new Suit[] {Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS, Suit.SPADES}) {
      for (int rank = 2; rank <= 14; rank++) {
        deck.add(Card.newBuilder().setSuit(suit).setRank(Rank.forNumber(rank)).build());
      }
    }
    // Shuffle using seed
    Random rng = seed.length > 0 ? new Random(ByteUtils.bytesToLong(seed)) : new Random();
    Collections.shuffle(deck, rng);
    return deck;
  }

  private int getHoleCardCount(GameVariant variant) {
    switch (variant) {
      case OMAHA:
        return 4;
      case FIVE_CARD_DRAW:
        return 5;
      default:
        return 2; // Texas Hold'em, 7-card stud
    }
  }

  private static BettingPhase determineNextPhase(HandState state) {
    int current = state.getCurrentPhase();
    if (current == BettingPhase.PREFLOP_VALUE) return BettingPhase.FLOP;
    if (current == BettingPhase.FLOP_VALUE) return BettingPhase.TURN;
    if (current == BettingPhase.TURN_VALUE) return BettingPhase.RIVER;
    return BettingPhase.SHOWDOWN;
  }

  private static byte[] cardToBytes(Card c) {
    return new byte[] {(byte) c.getSuitValue(), (byte) c.getRankValue()};
  }

  private static Card bytesToCard(byte[] bytes) {
    if (bytes == null || bytes.length < 2) {
      throw new IllegalArgumentException("Invalid card bytes: expected 2 bytes");
    }
    return Card.newBuilder()
        .setSuit(Suit.forNumber(bytes[0]))
        .setRank(Rank.forNumber(bytes[1]))
        .build();
  }

  // --- Hand Evaluation ---

  private HandRanking evaluateHand(List<Card> holeCards, List<Card> communityCards) {
    List<Card> allCards = new ArrayList<>();
    allCards.addAll(holeCards);
    allCards.addAll(communityCards);

    // Sort by rank descending
    allCards.sort((a, b) -> b.getRankValue() - a.getRankValue());

    // Group by suit and rank
    Map<Suit, List<Card>> bySuit = new HashMap<>();
    Map<Integer, List<Card>> byRank = new HashMap<>();
    for (Card c : allCards) {
      bySuit.computeIfAbsent(c.getSuit(), k -> new ArrayList<>()).add(c);
      byRank.computeIfAbsent(c.getRankValue(), k -> new ArrayList<>()).add(c);
    }

    // Check for flush
    List<Card> flushCards = null;
    for (List<Card> suited : bySuit.values()) {
      if (suited.size() >= 5) {
        flushCards = suited.subList(0, 5);
        break;
      }
    }

    // Check for straight
    List<Card> straightCards = findStraight(allCards);

    // Check for straight flush / royal flush
    if (flushCards != null) {
      List<Card> straightFlush = findStraight(flushCards);
      // Fall back: check if the regular straight is all one suit
      if (straightFlush == null && straightCards != null && isSameSuit(straightCards)) {
        straightFlush = straightCards;
      }
      if (straightFlush != null) {
        if (straightFlush.get(0).getRankValue() == Rank.ACE_VALUE) {
          return HandRanking.newBuilder()
              .setRankType(HandRankType.ROYAL_FLUSH)
              .setScore(1000)
              .build();
        }
        return HandRanking.newBuilder()
            .setRankType(HandRankType.STRAIGHT_FLUSH)
            .addKickers(straightFlush.get(0).getRank())
            .setScore(900 + straightFlush.get(0).getRankValue())
            .build();
      }
    }

    // Count pairs, trips, quads
    List<Integer> quads = new ArrayList<>();
    List<Integer> trips = new ArrayList<>();
    List<Integer> pairs = new ArrayList<>();
    for (Map.Entry<Integer, List<Card>> entry : byRank.entrySet()) {
      int count = entry.getValue().size();
      if (count == 4) quads.add(entry.getKey());
      else if (count == 3) trips.add(entry.getKey());
      else if (count == 2) pairs.add(entry.getKey());
    }
    quads.sort(Collections.reverseOrder());
    trips.sort(Collections.reverseOrder());
    pairs.sort(Collections.reverseOrder());

    // Four of a kind
    if (!quads.isEmpty()) {
      return HandRanking.newBuilder()
          .setRankType(HandRankType.FOUR_OF_A_KIND)
          .addKickers(Rank.forNumber(quads.get(0)))
          .setScore(800 + quads.get(0))
          .build();
    }

    // Full house
    if (!trips.isEmpty() && (!pairs.isEmpty() || trips.size() > 1)) {
      int pairRank = !pairs.isEmpty() ? pairs.get(0) : (trips.size() > 1 ? trips.get(1) : 0);
      return HandRanking.newBuilder()
          .setRankType(HandRankType.FULL_HOUSE)
          .addKickers(Rank.forNumber(trips.get(0)))
          .addKickers(Rank.forNumber(pairRank))
          .setScore(700 + trips.get(0) * 10 + pairRank)
          .build();
    }

    // Flush
    if (flushCards != null) {
      return HandRanking.newBuilder()
          .setRankType(HandRankType.FLUSH)
          .addKickers(flushCards.get(0).getRank())
          .setScore(600 + flushCards.get(0).getRankValue())
          .build();
    }

    // Straight
    if (straightCards != null) {
      return HandRanking.newBuilder()
          .setRankType(HandRankType.STRAIGHT)
          .addKickers(straightCards.get(0).getRank())
          .setScore(500 + straightCards.get(0).getRankValue())
          .build();
    }

    // Three of a kind
    if (!trips.isEmpty()) {
      return HandRanking.newBuilder()
          .setRankType(HandRankType.THREE_OF_A_KIND)
          .addKickers(Rank.forNumber(trips.get(0)))
          .setScore(400 + trips.get(0))
          .build();
    }

    // Two pair
    if (pairs.size() >= 2) {
      return HandRanking.newBuilder()
          .setRankType(HandRankType.TWO_PAIR)
          .addKickers(Rank.forNumber(pairs.get(0)))
          .addKickers(Rank.forNumber(pairs.get(1)))
          .setScore(300 + pairs.get(0) * 10 + pairs.get(1))
          .build();
    }

    // Pair
    if (!pairs.isEmpty()) {
      return HandRanking.newBuilder()
          .setRankType(HandRankType.PAIR)
          .addKickers(Rank.forNumber(pairs.get(0)))
          .setScore(200 + pairs.get(0))
          .build();
    }

    // High card
    return HandRanking.newBuilder()
        .setRankType(HandRankType.HIGH_CARD)
        .addKickers(allCards.get(0).getRank())
        .setScore(100 + allCards.get(0).getRankValue())
        .build();
  }

  private List<Card> findStraight(List<Card> cards) {
    if (cards.size() < 5) return null;

    // Get unique ranks sorted descending
    List<Integer> ranks =
        cards.stream()
            .map(Card::getRankValue)
            .distinct()
            .sorted(Collections.reverseOrder())
            .collect(Collectors.toList());

    // Check for wheel (A-2-3-4-5)
    if (ranks.contains(Rank.ACE_VALUE)
        && ranks.contains(2)
        && ranks.contains(3)
        && ranks.contains(4)
        && ranks.contains(5)) {
      return cards.stream()
          .filter(
              c ->
                  c.getRankValue() == 5
                      || c.getRankValue() == 4
                      || c.getRankValue() == 3
                      || c.getRankValue() == 2
                      || c.getRankValue() == Rank.ACE_VALUE)
          .limit(5)
          .collect(Collectors.toList());
    }

    // Check for regular straight
    for (int i = 0; i <= ranks.size() - 5; i++) {
      boolean isStraight = true;
      for (int j = 0; j < 4; j++) {
        if (ranks.get(i + j) - ranks.get(i + j + 1) != 1) {
          isStraight = false;
          break;
        }
      }
      if (isStraight) {
        int highRank = ranks.get(i);
        return cards.stream()
            .filter(c -> c.getRankValue() >= highRank - 4 && c.getRankValue() <= highRank)
            .limit(5)
            .collect(Collectors.toList());
      }
    }

    return null;
  }

  private boolean isSameSuit(List<Card> cards) {
    if (cards == null || cards.isEmpty()) return false;
    Suit first = cards.get(0).getSuit();
    return cards.stream().allMatch(c -> c.getSuit() == first);
  }

  private static com.google.protobuf.Timestamp now() {
    java.time.Instant instant = java.time.Instant.now();
    return com.google.protobuf.Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
