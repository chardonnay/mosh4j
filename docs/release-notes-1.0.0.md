# mosh4j 1.0.0

First stable release of **mosh4j** – a Java implementation of the [Mosh](https://mosh.org) (mobile shell) UDP/SSP protocol for Java 25+.

## Highlights

- **State Synchronization Protocol (SSP)** over UDP with AES-128-OCB encryption
- **Modular library**: use only protocol + crypto, or the full client/server stack
- **Compatible** with the official [mosh-server](https://github.com/mobile-shell/mosh) (C++)
- **Test server** and script to run mosh4j as a server for integration tests

## Modules

| Module | Description |
|--------|-------------|
| `mosh4j-protocol` | Protobuf definitions and generated DTOs (transport, user input, host input) |
| `mosh4j-crypto` | AES-128-OCB, nonce/seq handling, Mosh key decoding (Base64) |
| `mosh4j-transport` | SSP transport (instructions, state window, acks, fragmentation) |
| `mosh4j-terminal` | Terminal state (framebuffer, minimal ANSI parser) |
| `mosh4j-core` | Client and server session APIs, UDP datagram layer |

## Requirements

- **JDK 25** (LTS)

## Usage

- **Build:** `mvn clean package`
- **Tests:** `mvn test`
- **Test server:** `./scripts/run-mosh4j-server.sh [port]` – prints `MOSH CONNECT <port> <key>` for client connections

Use `MoshClientSession` or `MoshServerSession` from `mosh4j-core` with a UDP port and session key (e.g. from `ssh user@host mosh-server` or from the test server output).

## Artifacts

This release provides JARs built for **x86_64 (amd64)** and **ARM64**. JARs are portable; use the build that matches your deployment architecture or either one on any platform with Java 25.

## License

MIT. See [LICENSE](https://github.com/chardonnay/mosh4j/blob/main/LICENSE).
