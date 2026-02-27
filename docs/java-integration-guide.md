# mosh4j Java Integration Guide

This guide explains how to embed `mosh4j` into your Java application as a Mosh-compatible client (and optionally server-side component), with concrete examples.

## 1) What you integrate

`mosh4j` is split into modules. Most applications use `mosh4j-core`, which already pulls in protocol, crypto, transport, and terminal modules.

- `mosh4j-core`:
  - `MoshClientSession`: core client protocol/session API
  - `MoshTerminalFrontend`: higher-level frontend with background receive loop and ANSI output queue
- `mosh4j-terminal`:
  - `StatefulAnsiRenderer`: incremental ANSI renderer from framebuffer state

## 2) Prerequisites

- Java 21+
- A running `mosh-server` endpoint (or your own server implementation)
- A valid Mosh session tuple:
  - UDP host
  - UDP port
  - `MOSH_KEY` (22-char Base64 key)

## 3) Dependency setup

If you build inside the same multi-module repository, just depend on `mosh4j-core`.

```xml
<dependency>
  <groupId>org.mosh4j</groupId>
  <artifactId>mosh4j-core</artifactId>
  <version>2.0.0</version>
</dependency>
```

If you use a different version in your build, align all `org.mosh4j` artifacts to the same version.

## 4) Session bootstrap

Create `InetSocketAddress` and decode the key via `MoshKey.fromBase64(...)`.

```java
import org.mosh4j.core.MoshClientSession;
import org.mosh4j.crypto.MoshKey;

import java.net.InetSocketAddress;

InetSocketAddress serverAddr = new InetSocketAddress("127.0.0.1", 60001);
MoshKey key = MoshKey.fromBase64("YOUR_22_CHAR_BASE64_KEY");
MoshClientSession session = new MoshClientSession(serverAddr, key, 120, 40);
```

## 5) Integration patterns

### Pattern A: low-level session loop

Use this if you want direct control over polling, threading, and rendering.

```java
session.sendInitialWakeUp(); // recommended once after startup

while (session.isRunning()) {
    boolean progressed = session.receiveOnce();
    if (progressed) {
        // Option A: inspect framebuffer directly
        // Framebuffer fb = session.getFramebuffer();
    }

    // Send user keystrokes from your input layer:
    // session.sendUserInput(bytes);
}
```

Notes:
- `receiveOnce()` is poll-friendly due to internal UDP receive timeout.
- `sendInitialWakeUp()` helps when the server waits for first client input.

### Pattern B: frontend wrapper with rendered ANSI output

Use this for terminal-widget style integration where your app consumes ANSI strings.

```java
import org.mosh4j.core.MoshTerminalFrontend;

MoshTerminalFrontend frontend = new MoshTerminalFrontend(session);
frontend.sendInitialWakeUp();
frontend.start();

while (frontend.isRunning()) {
    String frame = frontend.takeRenderedOutput(250);
    if (frame != null) {
        // Write ANSI frame to your terminal surface
        System.out.print(frame);
    }
}
```

Lifecycle:
- `start()` launches a background receive thread.
- `close()` stops the frontend and closes the session.

### Pattern C: bring-your-own terminal emulator (raw host bytes)

If your UI already has terminal emulation, consume host bytes and bypass ANSI frame queue.

```java
frontend.start();

while (frontend.isRunning()) {
    byte[] hostBytes = frontend.takeHostBytes(250);
    if (hostBytes != null) {
        // Feed bytes into your existing terminal emulator
        customTerminal.feed(hostBytes);
    }
}
```

## 6) User input, resize, heartbeat

### Send keys

```java
frontend.sendUserInput("ls -la\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
```

### Send terminal resize

```java
frontend.sendResize(160, 48);
```

### Keepalive / idle heartbeat

```java
frontend.sendHeartbeat();
```

The heartbeat sends an ack-only protocol packet (no keystrokes), useful during idle periods.

## 7) Error handling and threading recommendations

- Wrap startup in `try/catch` and always `close()` resources.
- Treat receive/send failures as potentially transient network issues.
- Keep UI thread separate from network receive loop.
- If you use blocking queue methods (`takeRenderedOutput`, `takeHostBytes`), handle `InterruptedException`.

Example skeleton:

```java
try (MoshTerminalFrontend frontend = new MoshTerminalFrontend(session)) {
    frontend.sendInitialWakeUp();
    frontend.start();
    while (frontend.isRunning()) {
        String frame = frontend.takeRenderedOutput(250);
        if (frame != null) {
            uiTerminal.write(frame);
        }
    }
} catch (InterruptedException ie) {
    Thread.currentThread().interrupt();
} catch (Exception e) {
    // log + shutdown
}
```

## 8) Optional: embedding mosh4j server session

You can also embed `MoshServerSession` if your Java process acts as a server-side Mosh endpoint.

```java
import org.mosh4j.core.MoshServerSession;
import org.mosh4j.crypto.MoshKey;

MoshKey key = MoshKey.fromBase64("YOUR_22_CHAR_BASE64_KEY");
MoshServerSession server = new MoshServerSession(60001, key, 120, 40);

while (server.isRunning()) {
    server.receiveOnce();
    // feedHostOutput(...) from your PTY/process output pipeline
}
```

## 9) Practical checklist

- [ ] Decode and validate key with `MoshKey.fromBase64`
- [ ] Call `sendInitialWakeUp()` once after client startup
- [ ] Forward local keystrokes via `sendUserInput(...)`
- [ ] Forward window-size changes via `sendResize(...)`
- [ ] Consume either rendered ANSI frames or raw host bytes
- [ ] Close frontend/session cleanly on shutdown

