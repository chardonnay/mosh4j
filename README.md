# mosh4j

Java implementation of the [Mosh](https://mosh.org) (mobile shell) UDP/SSP protocol. [GitHub](https://github.com/chardonnay/mosh4j) Use it as a library to build Mosh-compatible clients or servers in Java 25+.

## Features

- **State Synchronization Protocol (SSP)** over UDP
- **AES-128-OCB** authenticated encryption (session key from mosh-server)
- Compatible with the official [mosh-server](https://github.com/mobile-shell/mosh) (C++)
- Modular layout: use only protocol + crypto, or full client/server stack
- Java 25 (LTS)

## Modules

| Module | Description |
|--------|-------------|
| `mosh4j-protocol` | Protobuf definitions and generated DTOs (transport, user input, host input) |
| `mosh4j-crypto` | AES-128-OCB, nonce/seq handling, key decoding (Base64) |
| `mosh4j-transport` | SSP transport (instructions, state window, acks, fragmentation) |
| `mosh4j-terminal` | Terminal state (framebuffer, minimal ANSI) |
| `mosh4j-core` | Client and server session APIs |

## Build

```bash
mvn clean package
```

Requires JDK 25.

## Tests

```bash
mvn test
```

## Test server

To run mosh4j as a test server (e.g. for integration tests or a second mosh4j client):

```bash
./scripts/run-mosh4j-server.sh [port]
```

- **Port:** Default is 60001. You can pass the port as the first argument (takes precedence) or set `MOSH_PORT`.
- **Key:** If `MOSH_KEY` is not set or invalid (must be 22 characters Base64), a random key is generated.
- The script builds the project, then prints `MOSH CONNECT <port> <key>` to stderr. Stop with Ctrl+C.

## Usage

After establishing a session (e.g. via `ssh user@host mosh-server`), you receive a UDP port and an AES key. Use `mosh4j-core` to create a `MoshClientSession` or `MoshServerSession` with that port and key.

See the [reference implementation](https://github.com/mobile-shell/mosh) for protocol details.

## Branch strategy

- `main` – main development
- `features/*` – new features
- `bugfixes/*` – bug fixes

Merge via Pull Request.

## License

MIT. See [LICENSE](LICENSE).
