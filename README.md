# angzarr-examples-java

Example implementations demonstrating Angzarr event sourcing patterns in Java. See the [Angzarr documentation](https://angzarr.io/) for more information.

> The poker example has been retired. A blackjack example is coming; its spec lives in [angzarr-project](https://github.com/angzarr-io/angzarr-project) under `proto/io/angzarr/examples/v1` and `features/example/blackjack*`.

## Development

Install git hooks (requires [lefthook](https://github.com/evilmartians/lefthook)):

```bash
lefthook install
```

### Recipes

```bash
just -l              # List all available recipes
just build           # Build (Gradle, in the devcontainer)
just test            # Run tests
just lint            # Run checks
just fmt             # Auto-format (Spotless)
```

## License

BSD-3-Clause
