package dev.angzarr.examples.acceptance;

import dev.angzarr.examples.client.TestContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;

/** Cucumber hooks for acceptance test lifecycle. */
public class AcceptanceHooks {

  static final TestContext CONTEXT = new TestContext();

  @Before
  public void setUp() {
    CONTEXT.reset();
  }

  @After
  public void tearDown() {
    // Context is reused across scenarios; reset clears per-scenario state
  }
}
