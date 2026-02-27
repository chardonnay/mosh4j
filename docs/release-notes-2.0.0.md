# mosh4j 2.0.0

This release consolidates all changes since `v1.0.0` and publishes the project as **2.0.0**.
It includes protocol hardening, terminal frontend APIs, CI improvements, security updates, and licensing/versioning updates.

## Highlights

- **Wire-format reliability improvements**
  - Improved initial client/server exchange behavior and state/ack sequencing.
  - Added more robust ack-only behavior and transient network failure handling.
- **Java terminal frontend**
  - Added `MoshTerminalFrontend` for queue-based integration.
  - Added `StatefulAnsiRenderer` for full redraw + incremental ANSI frame output.
  - Exposed raw host-byte consumption APIs for custom terminal emulators.
- **Security**
  - Upgraded `protobuf-java` to a patched version to address `CVE-2024-7254`.
- **CI/CD snapshots**
  - Added daily snapshot builds from `main`.
  - Fixed workflow detection/API access issues.
  - Added `amd64` + `arm64` matrix snapshot artifacts.
- **Documentation and project metadata**
  - Expanded Java integration documentation with practical embedding patterns and examples.
  - Switched license references and repository license file to GPL-3.0.
  - Bumped project/module versions to `2.0.0`.

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

- Release workflow publishes JARs for **x86_64 (amd64)** and **ARM64**.
- Daily snapshot workflow now also builds architecture-specific artifacts for both platforms.

## License

GNU General Public License v3.0. See [LICENSE](https://github.com/chardonnay/mosh4j/blob/main/LICENSE).
