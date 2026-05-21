# Java poker examples
#
# Container Overlay Pattern:
# --------------------------
# This justfile uses an overlay pattern for container execution:
#
# 1. `justfile` (this file) - runs on the host, delegates to container
# 2. `justfile.container` - mounted over this file inside the container
#
# When running outside a devcontainer:
#   - Builds/uses local devcontainer image with `just` pre-installed
#   - Podman mounts justfile.container as /workspace/justfile
#   - `just build` on host → docker runs → `just build` in container → gradlew
#
# When running inside a devcontainer (DEVCONTAINER=true):
#   - Commands execute directly via `just <target>`
#   - No container nesting

set shell := ["bash", "-c"]

# Reusable submodule-protection recipes (install-submodule-hooks,
# check-submodules-clean). Source of truth: angzarr-project/submodule.just.
import? 'angzarr-project/submodule.just'

ROOT := `git rev-parse --show-toplevel`
IMAGE := "angzarr-java-dev"

# Build the devcontainer image
[private]
_build-image:
    docker build --network=host -t {{IMAGE}} -f "{{ROOT}}/.devcontainer/Containerfile" "{{ROOT}}/.devcontainer"

# Run just target in container (or directly if already in devcontainer)
[private]
_container +ARGS: _build-image
    #!/usr/bin/env bash
    if [ "${DEVCONTAINER:-}" = "true" ]; then
        just {{ARGS}}
    else
        docker run --rm --network=host \
            -v "{{ROOT}}:/workspace:Z" \
            -v "{{ROOT}}/justfile.container:/workspace/justfile:ro" \
            -w /workspace \
            {{IMAGE}} just {{ARGS}}
    fi

default:
    @just --list

# =============================================================================
# Proto generation — cross-language model (project_proto_generation_model)
# =============================================================================
# `.proto` sources live in the angzarr-project submodule. Generated Java
# bindings are NEVER committed (gitignored — see .gitignore). They are
# regenerated:
#   1. on `post-checkout` / `post-merge` via lefthook (covers fresh clones,
#      branch switches, submodule bumps)
#   2. transparently as a recipe dependency of `build`, `test`, `fmt`, etc.
#      The recipe is idempotent — mtime guard skips when bindings are newer
#      than the newest .proto source.
#
# Runs in the same devcontainer image used for build/test so the
# protoc-gen-java + protoc-gen-grpc-java toolchain is fixed (no host fallback).
# Rootless docker requires `-u 0:0` per feedback_docker_rootless.
#
# Build-tool integration (the `com.google.protobuf` gradle plugin in the
# included build) is the EXECUTOR but NOT the trigger: this recipe invokes
# `gradle :angzarr-client-java:proto:generateProto` explicitly. Plain
# `gradle build` consumes the pre-emitted bindings. Keeping orchestration in
# `just` matches the 6-lang ecosystem pattern.

PROTO_SRC_DIR := ROOT + "/angzarr-project/proto"
# Generated Java sources land inside the included build (composite build), not
# the examples top-level. The angzarr-client-java submodule is read-only from
# this repo's perspective, but writing build output into it is fine — that
# tree is gitignored within the submodule.
PROTO_OUT_DIR := ROOT + "/angzarr-client-java/proto/build/generated/source/proto/main"

# Public entry point. Idempotent: returns immediately if bindings are
# fresher than the newest .proto source.
generate-proto:
    #!/usr/bin/env bash
    set -euo pipefail
    src_dir="{{PROTO_SRC_DIR}}"
    out_dir="{{PROTO_OUT_DIR}}"
    if [ ! -d "$src_dir" ]; then
        echo "[generate-proto] $src_dir missing — is the angzarr-project submodule initialized?" >&2
        exit 1
    fi
    # Staleness check: regenerate if any .proto file is newer than the
    # OLDEST generated binding, or if no bindings exist yet.
    # Catches "submodule bumped" and "fresh clone" — the hot paths driving
    # the lefthook trigger. Does NOT catch manual deletion of one binding
    # while others remain fresh; use `just generate-proto-force` for that.
    #
    # OLDEST (matches Python/Rust) — the Java generated tree lives entirely
    # under proto/build/generated/ which gradle wipes-and-regens on each
    # protoc invocation, so no orphan-stale leftovers exist.
    newest_proto=$(find "$src_dir" -name '*.proto' -printf '%T@\n' 2>/dev/null \
                    | sort -n | tail -1)
    # Guard the find for out_dir — on clean state the gradle build dir does
    # not yet exist, and `find $missing` exits non-zero which trips pipefail.
    if [ -d "$out_dir" ]; then
        # Use `awk 'NR==1'` (reads all input then prints first line) instead
        # of `head -1` (closes pipe early). With 748+ output files, head -1
        # causes find to receive SIGPIPE; under `set -o pipefail` that trips
        # the recipe. awk consumes the full stream so the pipe never closes
        # early.
        oldest_pb=$(find "$out_dir" -name '*.java' -printf '%T@\n' 2>/dev/null \
                        | sort -n | awk 'NR==1')
    else
        oldest_pb=""
    fi
    if [ -n "$newest_proto" ] && [ -n "$oldest_pb" ] \
        && awk -v p="$newest_proto" -v b="$oldest_pb" 'BEGIN{exit !(b>p)}'; then
        echo "[generate-proto] bindings up-to-date, skipping (use 'just generate-proto-force' to override)"
        exit 0
    fi
    just generate-proto-force

# Always regenerate, ignoring mtimes. Invoked by `generate-proto` when stale
# and exposed directly for users who want to force a rebuild.
generate-proto-force: _build-image
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "${DEVCONTAINER:-}" = "true" ]; then
        # Inside the devcontainer image already — run directly.
        just --justfile "{{ROOT}}/justfile.container" generate-proto-force
        exit 0
    fi
    # Rootless docker: -u 0:0 maps to host user via subuid; writes to the
    # bind-mount land owned by the host user. Rootful: direct uid match.
    # See feedback_docker_rootless.
    if docker info --format '{{{{.SecurityOptions}}}}' 2>/dev/null | grep -q rootless; then
        USER_FLAG="-u 0:0"
    else
        USER_FLAG="-u $(id -u):$(id -g)"
    fi
    docker run --rm --network=host \
        $USER_FLAG \
        -v "{{ROOT}}:/workspace" \
        -v "{{ROOT}}/justfile.container:/workspace/justfile:ro" \
        -w /workspace \
        -e DEVCONTAINER=true \
        {{IMAGE}} just generate-proto-force

# Legacy alias — kept so existing recipe-deps and muscle memory keep working.
proto: generate-proto

build: generate-proto
    just _container build

build-dev: generate-proto
    just _container build-dev

test-unit: generate-proto
    just _container test-unit

test-acceptance: generate-proto
    just _container test-acceptance

test: generate-proto
    just _container test

fmt: generate-proto
    just _container fmt

lint: generate-proto
    just _container lint

deps: generate-proto
    just _container deps

wrapper-update:
    just _container wrapper-update

# Run poker in standalone mode (host - needs Rust)
run: build
    mkdir -p "{{ROOT}}/data"
    cd "{{ROOT}}" && cargo run \
        --bin angzarr-standalone \
        --features standalone,sqlite \
        -- --config examples/java/standalone.yaml

clean:
    just _container "./gradlew clean" || true
    rm -rf "{{ROOT}}/data"

# Auto-format code
fmt-fix:
    just _container fmt-fix

# Cross-language alias — `just check` runs lint + fmt-check.
check: lint fmt
