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

`MoshClientSession` sets the UDP socket receive timeout to **250 ms** by default (`DEFAULT_UDP_RECEIVE_TIMEOUT_MS`). This converts the blocking `receiveOnce()` call into a poll-friendly loop that returns `null` on timeout instead of blocking indefinitely. To override the timeout, call `UdpDatagramChannel.setReceiveTimeoutMillis(int)` on the underlying channel before starting the receive loop, or change the `DEFAULT_UDP_RECEIVE_TIMEOUT_MS` constant in `MoshClientSession`.

**Sending an initial wake-up:**
Call `session.sendInitialWakeUp()` immediately after constructing/configuring `MoshClientSession` and before entering the receive loop. This sends a harmless single-byte keystroke packet that prompts the server to emit its first framebuffer update. If the server is already sending unsolicited updates (e.g. a shell prompt), the wake-up is unnecessary but harmless.

```java
MoshClientSession session = new MoshClientSession(serverAddr, key, 80, 24);
session.sendInitialWakeUp();  // trigger first server frame
while (session.isRunning()) {
    session.receiveOnce();
}
```

See the [reference implementation](https://github.com/mobile-shell/mosh) for protocol details.

## Branch strategy

- `main` – main development
- `features/*` – new features
- `bugfixes/*` – bug fixes

Merge via Pull Request.

## License

MIT. See [LICENSE](LICENSE).
