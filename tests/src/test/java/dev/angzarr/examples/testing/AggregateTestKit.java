package dev.angzarr.examples.testing;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.client.Errors;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.DispatchException;
import dev.angzarr.client.router.Router;
import io.grpc.Status;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Test harness for Tier 5 aggregates.
 *
 * <p>Wraps a {@link CommandHandlerRouter} around a single {@code @Aggregate}-annotated handler
 * class and exposes the three operations step-def style tests usually want:
 *
 * <ul>
 *   <li>{@link #handleCommand(Message)} — dispatch a command, return the first emitted event.
 *   <li>{@link #rehydrate(EventBook)} — replace the running event history.
 *   <li>{@link #state()} — materialize the current aggregate state by replaying events.
 * </ul>
 *
 * <p>Commands are wrapped into a {@link ContextualCommand} addressed to the aggregate's own domain
 * (read from {@code @Aggregate(domain = ...)}) and routed through the real runtime; any emitted
 * event is appended to the kit's in-memory event log so subsequent {@code state()} or {@code
 * handleCommand} calls see it.
 *
 * <p>Rejections thrown by handlers surface as {@link Errors.CommandRejectedError} to match the
 * legacy OO API that step defs expect.
 *
 * @param <H> the handler POJO class
 * @param <S> the state type declared via {@code @Aggregate(state = ...)}
 */
public final class AggregateTestKit<H, S> {

  private static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  private final Class<H> handlerClass;
  private final String domain;
  private final CommandHandlerRouter<S> router;
  private EventBook events;

  public AggregateTestKit(Class<H> handlerClass, Supplier<? extends H> factory) {
    this.handlerClass = handlerClass;
    Aggregate annotation = handlerClass.getAnnotation(Aggregate.class);
    if (annotation == null) {
      throw new IllegalArgumentException(handlerClass.getName() + " is missing @Aggregate");
    }
    this.domain = annotation.domain();
    @SuppressWarnings("unchecked")
    CommandHandlerRouter<S> built =
        (CommandHandlerRouter<S>)
            Router.newBuilder(domain + "-testkit").withHandler(handlerClass, factory).build();
    this.router = built;
    this.events = emptyBook();
  }

  /** Replace the event history and reset the kit's state cache. */
  public void rehydrate(EventBook book) {
    EventBook.Builder b = book.toBuilder();
    if (!b.hasCover()) {
      b.setCover(Cover.newBuilder().setDomain(domain));
    }
    if (b.getNextSequence() == 0 && b.getPagesCount() > 0) {
      b.setNextSequence(b.getPagesCount());
    }
    this.events = b.build();
  }

  /** Materialize the aggregate state by replaying the kit's event history. */
  public S state() {
    return router.rebuildStateFor(handlerClass, events);
  }

  /**
   * Dispatch {@code command} as a single-page {@link ContextualCommand}. Returns the first event
   * emitted (or {@code null} if none), and appends every emitted event to the internal history so
   * subsequent {@link #state()} calls reflect it.
   *
   * @throws Errors.CommandRejectedError if the handler rejected the command.
   */
  public Message handleCommand(Message command) throws Errors.CommandRejectedError {
    CommandBook book =
        CommandBook.newBuilder()
            .setCover(Cover.newBuilder().setDomain(domain))
            .addPages(CommandPage.newBuilder().setCommand(Any.pack(command, TYPE_URL_PREFIX)))
            .build();
    ContextualCommand ctx =
        ContextualCommand.newBuilder().setCommand(book).setEvents(events).build();
    BusinessResponse response;
    try {
      response = router.dispatch(ctx);
    } catch (DispatchException de) {
      throw unwrapRejection(de);
    }
    EventBook emitted = response.getEvents();
    if (emitted.getPagesCount() == 0) {
      return null;
    }
    appendToHistory(emitted);
    Any firstEvent = emitted.getPages(0).getEvent();
    return decodeEvent(firstEvent);
  }

  /** Current event history (for assertions). */
  public EventBook events() {
    return events;
  }

  // --- helpers ---

  private EventBook emptyBook() {
    return EventBook.newBuilder().setCover(Cover.newBuilder().setDomain(domain)).build();
  }

  private void appendToHistory(EventBook emitted) {
    EventBook.Builder b = events.toBuilder();
    int next = b.getNextSequence();
    for (EventPage page : emitted.getPagesList()) {
      b.addPages(page);
      next = Math.max(next + 1, page.getHeader().getSequence() + 1);
    }
    b.setNextSequence(next);
    this.events = b.build();
  }

  private Errors.CommandRejectedError unwrapRejection(DispatchException de) {
    // A rejection thrown inside a handler is wrapped by MethodHandle.invoke and re-wrapped by
    // the router as DispatchException(INTERNAL, ..., cause=runtime). Walk the cause chain.
    for (Throwable t = de; t != null; t = t.getCause()) {
      if (t instanceof Errors.CommandRejectedError cre) {
        return cre;
      }
    }
    // Not a business rejection — fall through to the dispatch-level status. Still raise as a
    // CommandRejectedError so step defs can handle it uniformly.
    Status.Code code = de.code();
    if (code == Status.Code.INVALID_ARGUMENT) {
      return new Errors.CommandRejectedError(de.getMessage(), Status.Code.INVALID_ARGUMENT);
    }
    return new Errors.CommandRejectedError(de.getMessage(), Status.Code.FAILED_PRECONDITION);
  }

  private Message decodeEvent(Any event) {
    String typeUrl = event.getTypeUrl();
    int slash = typeUrl.lastIndexOf('/');
    String fullName = slash < 0 ? typeUrl : typeUrl.substring(slash + 1);
    // Resolve the registered @Handles output type — failing that, fall back to brute-forcing
    // parseFrom on each registered event class. For simplicity we parse via reflection on the
    // concrete Message subclass whose descriptor matches.
    for (Method m : handlerClass.getDeclaredMethods()) {
      dev.angzarr.client.annotations.Handles h =
          m.getAnnotation(dev.angzarr.client.annotations.Handles.class);
      if (h == null) continue;
      Class<?> returnType = m.getReturnType();
      if (!Message.class.isAssignableFrom(returnType)) continue;
      try {
        Method getDesc = returnType.getMethod("getDescriptor");
        Object desc = getDesc.invoke(null);
        String fn = (String) desc.getClass().getMethod("getFullName").invoke(desc);
        if (fn.equals(fullName)) {
          Method parseFrom =
              returnType.getMethod("parseFrom", com.google.protobuf.ByteString.class);
          return (Message) parseFrom.invoke(null, event.getValue());
        }
      } catch (ReflectiveOperationException ignored) {
        // try next
      }
    }
    // Last resort: try unpacking via Any.is/unpack against each @Applies event class.
    for (Method m : handlerClass.getDeclaredMethods()) {
      dev.angzarr.client.annotations.Applies a =
          m.getAnnotation(dev.angzarr.client.annotations.Applies.class);
      if (a == null) continue;
      @SuppressWarnings("unchecked")
      Class<? extends Message> evtClass = (Class<? extends Message>) a.value();
      try {
        Method getDesc = evtClass.getMethod("getDescriptor");
        Object desc = getDesc.invoke(null);
        String fn = (String) desc.getClass().getMethod("getFullName").invoke(desc);
        if (fn.equals(fullName)) {
          Method parseFrom = evtClass.getMethod("parseFrom", com.google.protobuf.ByteString.class);
          return (Message) parseFrom.invoke(null, event.getValue());
        }
      } catch (ReflectiveOperationException ignored) {
        // try next
      }
    }
    throw new IllegalStateException(
        "cannot decode emitted event type " + fullName + " for " + handlerClass.getSimpleName());
  }

  /** Silenced helper for tests that want to feed arbitrary pre-packed events via Any. */
  @SuppressWarnings("unused")
  private static InvalidProtocolBufferException unused;
}
