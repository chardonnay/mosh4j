# mosh4j

Java implementation of the [Mosh](https://mosh.org) (mobile shell) UDP/SSP protocol.
Use it as a library to build Mosh-compatible clients or servers in Java 21+.

**Releases:** [v2.0.1](https://github.com/chardonnay/mosh4j/releases/tag/v2.0.1) — [release notes](docs/release-notes-2.0.1.md)

![mosh4j Architecture](docs/images/mosh4j-architecture.png)

## Features

- **State Synchronization Protocol (SSP)** over UDP — the same wire protocol as the official [mosh](https://github.com/mobile-shell/mosh) C++ implementation
- **AES-128-OCB** authenticated encryption with automatic nonce management
- **Roaming support** — seamless IP/network changes, just like native Mosh
- **Three integration patterns** — consume rendered ANSI frames, raw host bytes, or direct framebuffer access
- **Terminal emulation** — built-in ANSI parser and stateful incremental renderer
- **Modular design** — use only the layers you need (crypto-only, transport-only, or full stack)
- **Java 21 (LTS)** — no native dependencies, pure Java

## Architecture

mosh4j is organized in five Maven modules that mirror the Mosh protocol layers:

| Module | Description | Key Classes |
|--------|-------------|-------------|
| `mosh4j-protocol` | Protobuf definitions and generated DTOs | `TransportBuffers`, `ClientBuffers`, `HostBuffers` |
| `mosh4j-crypto` | AES-128-OCB cipher, nonce handling, key decoding | `MoshKey`, `SspCipher`, `Nonce` |
| `mosh4j-transport` | SSP transport layer (state sync, acks, fragmentation) | `TransportSender`, `TransportReceiver`, `FragmentCodec` |
| `mosh4j-terminal` | Terminal state management and ANSI rendering | `Framebuffer`, `SimpleFramebuffer`, `StatefulAnsiRenderer` |
| `mosh4j-core` | High-level session APIs and UDP datagram handling | `MoshClientSession`, `MoshServerSession`, `MoshTerminalFrontend` |

Most applications only depend on `mosh4j-core`, which transitively pulls in all other modules.

## Quick Start

### 1. Add the dependency

```xml
<dependency>
  <groupId>org.mosh4j</groupId>
  <artifactId>mosh4j-core</artifactId>
  <version>2.0.1</version>
</dependency>
```

### 2. Connect to a mosh-server

First, start a mosh-server on the remote host (via SSH or any other bootstrap method). The server prints a UDP port and a Base64 key.

```java
import org.mosh4j.core.MoshClientSession;
import org.mosh4j.core.MoshTerminalFrontend;
import org.mosh4j.crypto.MoshKey;
import java.net.InetSocketAddress;

InetSocketAddress server = new InetSocketAddress("192.168.1.100", 60001);
MoshKey key = MoshKey.fromBase64("4kYMa9v+P1lOQ0Uy7A==");

MoshClientSession session = new MoshClientSession(server, key, 80, 24);
MoshTerminalFrontend frontend = new MoshTerminalFrontend(session);
frontend.sendInitialWakeUp();
frontend.start();

while (frontend.isRunning()) {
    String frame = frontend.takeRenderedOutput(250);
    if (frame != null) {
        System.out.print(frame);
    }
}
```

### 3. Send user input and handle resize

```java
frontend.sendUserInput("ls -la\n".getBytes(StandardCharsets.UTF_8));
frontend.sendResize(160, 48);
frontend.sendHeartbeat();
```

## Integration Patterns

mosh4j supports three patterns depending on your application's needs:

![mosh4j Integration Patterns](docs/images/mosh4j-integration-patterns.png)

| Pattern | Use Case | API |
|---------|----------|-----|
| **A: ANSI Frames** | Terminal widget that accepts ANSI escape sequences | `MoshTerminalFrontend.takeRenderedOutput()` |
| **B: Raw Host Bytes** | App with its own terminal emulator (e.g. xterm.js, hterm) | `MoshTerminalFrontend.takeHostBytes()` |
| **C: Low-Level** | Custom control over receive loop and framebuffer | `MoshClientSession.receiveOnce()` + `getFramebuffer()` |

For detailed examples of each pattern, including a complete korTTY-style terminal application, see the [Java Integration Guide](docs/java-integration-guide.md).

## Protocol Stack

![mosh4j Protocol Stack](docs/images/mosh4j-protocol-stack.png)

Every datagram passes through the full protocol stack: protobuf serialization, zlib-compressed fragmentation, SSP timestamping, and AES-128-OCB authenticated encryption — identical to the official Mosh wire format.

## Build

```bash
mvn clean package
```

Requires JDK 21.

## Tests

```bash
mvn test
```

## Test Server

Run mosh4j as a standalone test server for integration tests:

```bash
./scripts/run-mosh4j-server.sh [port]
```

- **Port:** Default `60001`. Override via argument or `MOSH_PORT` env var.
- **Key:** If `MOSH_KEY` is not set (or invalid), a random 128-bit key is generated.
- Prints `MOSH CONNECT <port> <key>` to stderr. Stop with Ctrl+C.

## Documentation

- [Java Integration Guide](docs/java-integration-guide.md) — comprehensive embedding guide with korTTY-style examples
- [Release Notes v2.0.1](docs/release-notes-2.0.1.md) — latest changes

## Branch Strategy

- `main` — stable development
- `features/*` — new features
- `bugfixes/*` — bug fixes

Merge via Pull Request.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
