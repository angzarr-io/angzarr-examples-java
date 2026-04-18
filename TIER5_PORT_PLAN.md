# Tier 5 Port Plan — examples-java

Phase follow-on to `client-java`'s Tier 5 Phase 4 migration (commit `80b11fb` on
`feat/cross-language-unification`). The client submodule is now pinned to the Tier 5
surface; every module in this repo currently fails to compile because it extends the
OO base classes that were deleted. This plan ports them one at a time.

## What changed in the client

Deleted surface (all references in this repo must be rewritten):

- Base classes: `CommandHandler`, `Saga`, `ProcessManager`, `Projector`
- Interfaces: `CommandHandlerDomainHandler`, `SagaDomainHandler`,
  `ProcessManagerDomainHandler`, `ProjectorDomainHandler`
- Gen-1 routers: `CommandRouter`, `EventRouter`
- CloudEvents: `CloudEventsProjector`, `CloudEventsRouter`
- Annotations: `@Publishes`, `@Projects`, `@Prepares`, `@Upcasts`

New/retained surface:

- Class annotations: `@Aggregate(domain, state)`, `@Saga(name, source, target)`,
  `@ProcessManager(name, pmDomain, sources, targets, state)`, `@Projector(name, domains)`
- Method annotations: `@Handles(Class)`, `@Applies(Class)`, `@Rejected(domain, command)`,
  `@StateFactory`
- Builder: `Router.newBuilder(name).withHandler(cls, factory).build() → Built`
- Runtime routers: `CommandHandlerRouter<S>`, `SagaRouter`, `ProcessManagerRouter<S>`,
  `ProjectorRouter`
- gRPC adapters: `CommandHandlerGrpc`, `SagaGrpc`, `ProcessManagerGrpc`, `ProjectorGrpc`
- Upcaster: `UpcasterRouter` (Rust-canonical fluent API `.on(suffix, lambda)` +
  `.onWith(supplier)`), `UpcasterGrpcHandler`
- Response types: `SagaHandlerResponse`, `ProcessManagerResponse` (unchanged)
- Helpers: `StateRouter`, `Destinations`, `SagaContext`, `CommandRejectedError`

## Module inventory

| Module | Kind (target) | Files | Risk | Notes |
|---|---|---:|---|---|
| `player/upc` | upcaster | 1 | low | Passthrough stub; already fluent. |
| `hand/upc` | upcaster | 1 | low | Passthrough stub; already fluent. |
| `player/agg` | `@Aggregate` | 13 | high | Biggest aggregate; foundational for every saga. |
| `table/agg` | `@Aggregate` | 4 | medium | |
| `hand/agg` | `@Aggregate` | 4 | medium | Heavy poker logic (phase transitions, betting). |
| `tournament/agg` | `@Aggregate` | 10 | medium | Tournament lifecycle. |
| `player/saga-table` | `@Saga` | 2 | low | Fact propagation. |
| `table/saga-hand` | `@Saga` | 2 | low | HandStarted → DealCards. |
| `table/saga-player` | `@Saga` | 2 | low | |
| `hand/saga-table` | `@Saga` | 2 | low | Fact injection. |
| `hand/saga-player` | `@Saga` | 2 | low | |
| `hand-flow` | `@ProcessManager` | 2 | medium | In-memory process tracking via state map. |
| `prj-output` | `@Projector` | 4 | medium | `handle` + `handleSpeculative` via `ProjectorGrpc`. |
| `tournament/agg` | `@Aggregate` | 10 | medium | |
| `tests` | — | — | low | Cucumber BDD; re-runs after modules compile. |

Modules to delete without porting:

- `hand-flow-oo/`, `table/saga-hand-oo/`, `prj-output-oo/`, `pmg-hand-flow/`,
  `pmg-hand-flow-oo/` — stale OO shadows of the live modules.
- `prj-cloudevents/` — Rust-only subsystem now; unused and not in
  `settings.gradle.kts` anyway.

## Per-module port recipe

Same shape for every aggregate / saga / PM / projector:

1. Replace `extends CommandHandler<State>` (or `Saga`, `ProcessManager<S>`, `Projector`)
   with `@Aggregate(domain=..., state=State.class)` (or equivalent) on a plain POJO.
2. Rewrite method annotations:
   - Aggregate handler methods take `(Cmd, State, long seq) -> Event | EventBook |
     List<Event>`.
   - `@Applies(Event.class)` methods take `(State, Event) -> void` and mutate state in
     place.
   - `@Rejected(domain, command)` methods take `(Notification, State) ->
     BusinessResponse` (or `RejectionHandlerResponse` for saga/PM).
   - `@Prepares` methods: **delete**. In Tier 5 PMs stamp destinations inline inside
     `@Handles`, matching Python.
3. If state construction is non-trivial, add an `@StateFactory` no-arg instance method
   returning `State`. Otherwise Tier 5 calls `State.class.getDeclaredConstructor()`.
4. In `Main.java`, replace the old `extends *ServiceImplBase` + manual dispatch with:
   ```java
   @GrpcService
   public static class PlayerHandlerService extends CommandHandlerGrpc {
       public PlayerHandlerService() {
           super((CommandHandlerRouter<PlayerState>) Router.newBuilder("player-agg")
               .withHandler(Player.class, Player::new)
               .build());
       }
   }
   ```
5. `@Publishes` in a CloudEvents projector → **delete the module**. If an integration
   needs it later, rebuild as a plain `@Projector` that serializes to the external
   system inside `@Handles` methods.
6. Upcaster modules: no changes expected — the fluent `UpcasterRouter.on(suffix,
   lambda)` API is preserved verbatim, and the two existing modules are passthrough
   stubs. Spot-check that the `new UpcasterRouter("...")` constructor still resolves.

## Recommended order

1. **Upcasters first** (`player/upc`, `hand/upc`) — shortest path to a green module, confirms the submodule bump works.
2. **`player/agg`** — largest and most-depended-on; unblocks every player saga.
3. **Remaining aggregates** in parallel: `table/agg`, `hand/agg`, `tournament/agg`.
4. **Sagas** — all five modules are small; mechanical `@Saga` + `@Handles(Event.class)` + `destinations.stampCommand(cmd, "targetDomain")`.
5. **`hand-flow`** — single `@ProcessManager` with state map; validates multi-source + PM response merge.
6. **`prj-output`** — last aggregate-style module; exercises `ProjectorGrpc`'s `handle`
   + `handleSpeculative` wiring.
7. **Delete** `*-oo/`, `pmg-hand-flow*/`, `prj-cloudevents/`.
8. **Run `./gradlew build`** — all modules compile + unit tests green.
9. **Run Cucumber suite** under `tests/` — re-syncs Gherkin against the new annotations.

## Effort estimate

Roughly 1–2 days of focused work. File counts are small (1–13 per module) and the
migration is mechanical once the first aggregate + first saga are proven. The player
aggregate is the critical path and the highest-risk single task; everything else is
a variation on it.

## Status

All 13 live modules ported to Tier 5. `./gradlew build` clean.

- **Main sources**: aggregates (player, table, hand, tournament), sagas (5 modules),
  hand-flow PM, prj-output projector, upcasters. All use `@Aggregate` / `@Saga` /
  `@ProcessManager` / `@Projector` POJOs wired via `Router.newBuilder(...).build()`
  and exposed over gRPC via the Tier 5 `CommandHandlerGrpc` / `SagaGrpc` /
  `ProcessManagerGrpc` / `ProjectorGrpc` adapters.
- **Dead modules deleted**: `*-oo/` shadows, `pmg-hand-flow*/`, `prj-cloudevents/`.

## Remaining work: Cucumber test step defs (deferred)

The `tests/` subproject is **excluded from the composite build** (commented out in
`settings.gradle.kts`). Its 2.4 KLOC of Cucumber step definitions are written
against the old OO aggregate API (`player.handleCommand(cmd)`,
`player.rehydrate(book)`, OO state accessors like `player.getBankroll()`). Porting
them to the Tier 5 Router-based pattern is a distinct follow-on task.

Recommended approach:

1. Write a small `AggregateTestKit<H, S>` helper in `tests/src/test/java/dev/angzarr/examples/testing/`
   that wraps a `CommandHandlerRouter<S>` and exposes `handleCommand(Message)`,
   `rehydrate(EventBook)`, and `state()` for ergonomic unit-style testing.
2. Replace `private Player player = new Player()` with
   `private AggregateTestKit<Player, PlayerState> player = new AggregateTestKit<>(Player.class, Player::new)`
   across the four step files.
3. Replace `player.getBankroll()` → `player.state().getBankroll()` etc.
4. Re-enable `include("tests")` in `settings.gradle.kts`.

## Doneness gate

- [x] `./gradlew build` clean on the root composite build.
- [x] `prj-cloudevents/`, `*-oo/`, `pmg-hand-flow*/` deleted.
- [x] `settings.gradle.kts` reflects the surviving module set.
- [ ] Cucumber step defs ported (deferred — see above).
- [ ] README updated to reference `@Aggregate`/`@Saga`/etc. patterns (optional).
