package dev.angzarr.examples.hand;

import dev.angzarr.client.UpcasterRouter;

/**
 * Hand domain upcaster — transforms old event versions to current during replay.
 *
 * <p>The upcaster is generic: parameterized by domain name, not tied to hand
 * specifically. The same pattern applies to any domain's schema evolution.
 *
 * <p>Currently passthrough — register transformation functions as schema changes:
 *
 * <pre>{@code
 * router.on("CardsDealtV1", old -> {
 *     CardsDealtV1 v1 = old.unpack(CardsDealtV1.class);
 *     return Any.pack(CardsDealt.newBuilder()
 *         .setTableRoot(v1.getTableRoot())
 *         .setGameVariant(GameVariant.TEXAS_HOLDEM)
 *         .build());
 * });
 * }</pre>
 */
public final class HandUpcaster {

  private HandUpcaster() {}

  public static UpcasterRouter createRouter() {
    return new UpcasterRouter("hand");
    // Register transformations as schema evolves:
    // .on("CardsDealtV1", HandUpcaster::upcastCardsDealt)
  }
}
