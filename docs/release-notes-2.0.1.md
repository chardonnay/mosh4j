# mosh4j 2.0.1

This release includes Java 21 alignment, security and robustness fixes, full GPL-3.0 license text, and framebuffer state parsing hardening since `v2.0.0`.

## Highlights

- **License**
  - LICENSE file now contains the full GPL-3.0 license text (from https://www.gnu.org/licenses/gpl-3.0.txt).
  - SPDX-License-Identifier: GPL-3.0-only.
  - Note added that the project was previously distributed under the MIT License; the switch to GPL-3.0 is effective as of 2025.
- **Java 21 alignment and security audit**
  - Project aligned to Java 21: POMs, workflows, and docs use JDK 21 (LTS).
  - MinimalAnsiParser: cap CSI parameters (max 32, value ≤ 65536) to prevent DoS.
  - MoshServerMain: redact session key unless `MOSH_PRINT_KEY=1`; validate port parsing.
  - MoshClientSession / MoshServerSession: log decode/process failures at FINE instead of ignoring.
  - MoshClientSession: log ack-only send failures at FINE.
  - SimpleFramebuffer.fromStateBytes: catch NumberFormatException, validate width/height, and clamp cursor; reject malformed input via early return.
- **SimpleFramebuffer state parsing**
  - When the cursor header is invalid (missing newline or missing `C` in valid position), the method returns immediately and does not parse cell data, avoiding corruption or reading mid-header.
  - Cursor row/column parsing wrapped in try/catch with safe defaults (0,0) on NumberFormatException; values clamped to valid range.

## Changes since v2.0.0

- Java 21 alignment and security audit fixes (#5).
- LICENSE: full GPL-3.0 text and prior MIT note; SimpleFramebuffer: discard invalid header, safe cursor parse.
- SimpleFramebuffer: reject malformed cursor header (return instead of parsing mid-header).

## Modules

| Module | Description |
|--------|-------------|
| `mosh4j-protocol` | Protobuf definitions and generated DTOs (transport, user input, host input) |
| `mosh4j-crypto` | AES-128-OCB, nonce/seq handling, Mosh key decoding (Base64) |
| `mosh4j-transport` | SSP transport (instructions, state window, acks, fragmentation) |
| `mosh4j-terminal` | Terminal state (framebuffer, minimal ANSI parser) |
| `mosh4j-core` | Client and server session APIs, UDP datagram layer |

## Requirements

- **JDK 21** (LTS)

## Usage

- **Build:** `mvn clean package`
- **Tests:** `mvn test`
- **Test server:** `./scripts/run-mosh4j-server.sh [port]` – prints `MOSH CONNECT <port> <key>` for client connections

Use `MoshClientSession` or `MoshServerSession` from `mosh4j-core` with a UDP port and session key (e.g. from `ssh user@host mosh-server` or from the test server output).

See also: [`docs/java-integration-guide.md`](./java-integration-guide.md)

## Artifacts

- Release workflow publishes JARs for **x86_64 (amd64)** and **ARM64** when a GitHub Release is published for this tag.

## License

GNU General Public License v3.0. See [LICENSE](https://github.com/chardonnay/mosh4j/blob/main/LICENSE).
