# Security Best Practices Report fuer mosh4j

Datum: 2026-06-08

## Executive Summary

Das Repository `/Users/daniel/Cursor/mosh4j` ist ein Java-21/Maven-Multi-Modulprojekt fuer ein Mosh UDP/SSP-Protokoll. Die im angeforderten Skill enthaltenen Referenzen decken Python, JavaScript/TypeScript und Go ab, aber keine Java-spezifische Referenzdatei. Dieser Report stuetzt sich daher auf lokale Codebelege, ausgefuehrte Validierungen und aktuelle primaere Quellen wie OWASP, Oracle/JDK, Protobuf, GitHub Docs, OSV und Bouncy Castle.

Es wurden keine offensichtlichen im Repository gespeicherten Secrets gefunden. Positive Sicherheitskontrollen sind vorhanden: `SecureRandom` fuer Session-Keys, Key-Redaction im Testserver, AES-OCB AEAD-Tag-Pruefung und Limits fuer ANSI-CSI-Parameter. Die wichtigsten offenen Punkte sind eine bekannte verwundbare Bouncy-Castle-Version, fehlender Replay-/Freshness-Schutz vor state-mutierenden Callbacks und fehlende Groessenlimits nach zlib-Dekomprimierung.

## Scope und Methodik

Geprueft wurden:

- Maven/POMs, direkte und transitive Dependency-Sicht ueber `mvn -B dependency:tree`.
- Crypto-, Datagramm-, Transport-, Terminal- und Session-Code unter `mosh4j-*`.
- GitHub Actions Workflows unter `.github/workflows`.
- Offensichtliche Secrets mit `rg`.
- Bestehende Tests mit `mvn test`.
- Direkter OSV-Spotcheck fuer deklarierte Dependencies/Build-Plugins.

Nicht geprueft bzw. nicht belegbar aus dem lokalen Kontext:

- Ob GitHub Dependabot, branch protection, required reviews oder org-weite Actions-Policies ausserhalb des Repos aktiviert sind.
- Produktions-Deployment, Netzwerkfilter, Rate Limits, Observability und erwartete maximale Terminal-/Payload-Groessen.
- Vollstaendige SCA/SBOM-Abdeckung aller transitiven Build-Tool-Abhaengigkeiten. Der OSV-Check war ein direkter Spotcheck, kein Ersatz fuer eine CI-SCA.

Validierter Status:

- `mvn test`: erfolgreich, 15 Tests, 0 Fehler, 0 Skips.
- `mvn -B dependency:tree`: erfolgreich.
- OSV `querybatch`: Treffer fuer `org.bouncycastle:bcprov-jdk18on:1.78.1`, keine Treffer fuer die weiteren direkt abgefragten Artefakte.

## High Severity

### H-1: Bekannte verwundbare Bouncy-Castle-Version wird als Runtime-Dependency ausgeliefert

Beleg:

- `mosh4j-crypto/pom.xml:19-21` deklariert `org.bouncycastle:bcprov-jdk18on:1.78.1`.
- `mvn -B dependency:tree` zeigt dieselbe Version als Compile-Dependency von `mosh4j-crypto` und transitiv von `mosh4j-core`.
- OSV meldete fuer diese Version:
  - `GHSA-p93r-85wp-75v3` / `CVE-2026-5598`, GitHub severity `HIGH`, betroffen `bcprov-jdk18on` ab 1.71 vor 1.84.
  - `GHSA-c3fc-8qff-9hwx` / `CVE-2026-0636`, GitHub severity `MODERATE`, betroffen `bcprov-jdk18on` ab 1.74 vor 1.84.
- Maven Central Metadata fuer `bcprov-jdk18on` nennt `1.84` als `latest` und `release`.

Bewertung:

Die direkte Ausnutzbarkeit im aktuell gelesenen mosh4j-Codepfad ist nicht bewiesen: der Code nutzt Bouncy Castle fuer AES-OCB in `mosh4j-crypto/src/main/java/org/mosh4j/crypto/SspCipher.java:25-29`, waehrend die Advisories LDAPStoreHelper bzw. FrodoKEM betreffen. Trotzdem wird ein bekannter verwundbarer Crypto-Provider als Runtime-Artefakt ausgeliefert. Downstream-Anwendungen koennen denselben Jar im Classpath fuer weitere Bouncy-Castle-APIs nutzen.

Empfehlung:

- `bcprov-jdk18on` auf `1.84` oder eine neuere offiziell verfuegbare Fix-Version anheben.
- Danach `mvn test` und einen SCA-Scan in CI ausfuehren.
- Wenn Kompatibilitaet zu bestimmten Bouncy-Castle-Versionen erforderlich ist, diese Anforderung im Projekt dokumentieren und die CVE-Auswirkung explizit begruenden.

Validierungspfad:

1. Pruefen: `nl -ba mosh4j-crypto/pom.xml | sed -n '17,22p'`.
2. Reproduzieren: OSV Query fuer `org.bouncycastle:bcprov-jdk18on` Version `1.78.1` ausfuehren.
3. Nach Fix: `mvn -B dependency:tree` muss `bcprov-jdk18on:1.84` oder neuer zeigen.
4. Regression: `mvn test`.

## Medium Severity

### M-1: Empfangene Datagramm-Sequenzen werden nicht als Replay-/Freshness-Grenze genutzt

Beleg:

- `mosh4j-core/src/main/java/org/mosh4j/core/datagram/SspDatagramCodec.java:72-74` extrahiert Richtung und Sequenz aus dem Nonce.
- `mosh4j-core/src/main/java/org/mosh4j/core/MoshClientSession.java:214-226` dekodiert und verarbeitet Server-Payloads, nutzt `payload.getSeq()` aber nur im Debug-Logging bei `mosh4j-core/src/main/java/org/mosh4j/core/MoshClientSession.java:232-240`.
- `mosh4j-core/src/main/java/org/mosh4j/core/MoshServerSession.java:86-106` verarbeitet Client-Payloads ohne Sequenz-Freshness-Pruefung.
- `mosh4j-transport/src/main/java/org/mosh4j/transport/TransportReceiver.java:46-47` ruft den `applyDiff`-Callback auf, bevor `newNum > latestStateNum` in `mosh4j-transport/src/main/java/org/mosh4j/transport/TransportReceiver.java:52-56` geprueft wird.
- Der Client-Callback hat sichtbare Seiteneffekte: `mosh4j-core/src/main/java/org/mosh4j/core/MoshClientSession.java:100-102` fuettert Host-Bytes in den Framebuffer und in die Queue.

Bewertung:

AEAD verhindert Forgery, aber keine Byte-fuer-Byte-Replays bereits beobachteter Datagramme. Ein Angreifer, der gueltige Pakete mitschneiden und erneut senden kann, braucht den Session-Key nicht, um diese alten Ciphertexte erneut einzuspielen. Der konkrete Effekt im aktuellen Code ist mindestens doppelte Callback-Ausfuehrung; bei Terminaldaten kann das zu doppelter Ausgabe fuehren. Fuer zukuenftige Server-Integrationen, die Client-Diffs als Eingaben weiterreichen, waere der Integritaetseffekt groesser.

Reproduzierter Minimalbeleg:

Ein JShell-Check mit den kompilierten Klassen hat dieselbe `Transportinstruction.Instruction` zweimal an `TransportReceiver.receive(...)` uebergeben. Ergebnis: `ack1=1, calls=1` und danach `ack2=1, calls=2`. Der Diff-Callback laeuft also auch beim Replay erneut.

Empfehlung:

- Vor `TransportReceiver.receive(...)` pro Richtung die hoechste akzeptierte Datagramm-Sequenz oder ein kleines Replay-Fenster pruefen und Duplikate/stale Sequenzen verwerfen.
- Zusaetzlich `TransportReceiver.receive(...)` so umbauen, dass `applyDiff` erst nach einer Freshness-/State-Akzeptanzentscheidung ausgefuehrt wird.
- Tests fuer Replay, out-of-order und legitime Retransmissionen ergaenzen, weil Mosh/SSP retransmissionssensitiv ist.

Validierungspfad:

1. Pruefen: `rg -n "getSeq\\(|payload\\.getSeq|inputReceiver\\.receive|outputReceiver\\.receive|applyDiff" mosh4j-core/src/main/java mosh4j-transport/src/main/java`.
2. Reproduzieren: dieselbe `TransportInstruction.create(0, 1, 0, 0, ...)` zweimal an einen `TransportReceiver` mit zaehlendem Callback senden.
3. Nach Fix: Wiederholung darf den Callback fuer Replay nicht erneut ausfuehren, muss aber legitime neuere State-Nummern weiter akzeptieren.

### M-2: Zlib-Dekomprimierung und Protobuf-Parsing haben keine sichtbare maximale Ausgabegroesse

Beleg:

- `mosh4j-core/src/main/java/org/mosh4j/core/datagram/FragmentCodec.java:156-176` dekomprimiert in eine `ByteArrayOutputStream` bis `Inflater.finished()` ohne `maxOutputBytes`.
- `mosh4j-core/src/main/java/org/mosh4j/core/datagram/FragmentCodec.java:209-217` assembliert Fragmentdaten ohne Gesamtgroessenlimit.
- `mosh4j-transport/src/main/java/org/mosh4j/transport/TransportInstruction.java:25-30` parst Protobuf aus `byte[]` bzw. `InputStream`; fuer den `byte[]`-Pfad ist im Projekt kein Size-Limit erkennbar.
- `mosh4j-core/src/main/java/org/mosh4j/core/datagram/UdpDatagramChannel.java:48-57` akzeptiert UDP-Pakete bis 65,507 Bytes, was fuer stark komprimierbare Payloads ausreichend ist, um deutlich groessere dekomprimierte Daten zu erzeugen.

Bewertung:

Unverschluesselte Angreifer erreichen die Dekomprimierung nicht, weil `SspDatagramCodec.decode(...)` vorher AEAD-dekryptiert. Ein boesartiger oder kompromittierter authentifizierter Peer bzw. jemand mit gueltigem Session-Key kann aber kleine komprimierte Eingaben zu grossen Heap-Allokationen expandieren. OWASP empfiehlt fuer DoS-Resilienz klare Request-/Input-Groessenlimits; Protobuf selbst empfiehlt, Message-Groessenlimits so klein wie funktional moeglich zu setzen, wobei `setSizeLimit` nicht fuer raw byte-array backed parsing gilt.

Reproduzierter Minimalbeleg:

Ein JShell-Check mit `FragmentCodec.encodeSingle(7L, new byte[2_000_000])` erzeugte ein Fragment von 1,970 Bytes und `new FragmentCodec().decode(fragment)` gab 2,000,000 Bytes zurueck. Dabei griff kein projektspezifisches Limit.

Empfehlung:

- Eine explizite maximale dekomprimierte Transport-Instruction-Groesse definieren.
- `zlibDecompress` nach jedem Inflate-Schritt abbrechen, sobald das Limit ueberschritten wird.
- Auch Fragment-Assembly-Gesamtgroesse und Protobuf-Diff-/HostBytes-Groessen begrenzen.
- Erwartete Limits aus dem Mosh-Protokoll bzw. aus realen Terminal-Frames ableiten und dokumentieren, statt willkuerliche Werte zu raten.

Validierungspfad:

1. Pruefen: `nl -ba mosh4j-core/src/main/java/org/mosh4j/core/datagram/FragmentCodec.java | sed -n '156,176p'`.
2. Reproduzieren: mit JShell einen stark komprimierbaren Payload dekodieren und `decoded.length` beobachten.
3. Nach Fix: derselbe Test mit Payload ueber Limit muss kontrolliert mit einer erwarteten Exception oder einem `null`/drop-Pfad enden.
4. Regression: normale kleine `TransportInstruction`-Roundtrips muessen weiter funktionieren.

## Low Severity

### L-1: Server prueft `protocol_version` nicht, Client schon

Beleg:

- `mosh4j-transport/src/main/java/org/mosh4j/transport/TransportInstruction.java:85-90` stellt `isProtocolVersionValid(...)` bereit.
- `mosh4j-core/src/main/java/org/mosh4j/core/MoshClientSession.java:221-224` verwirft Instructions mit falscher Version.
- `mosh4j-core/src/main/java/org/mosh4j/core/MoshServerSession.java:103-106` parst und verarbeitet Instructions ohne entsprechende Version-Pruefung.

Bewertung:

Dies ist keine nachgewiesene unauthentifizierte Schwachstelle, weil das Paket zuvor authentifiziert entschluesselt werden muss. Es ist aber eine Protokoll-Haertungsluecke und asymmetrisch zum Client. Ein authentifizierter Peer kann Server-seitig Daten mit fehlender oder falscher `protocol_version` in die State-Maschine geben.

Empfehlung:

- In `MoshServerSession.receiveOnce()` dieselbe `TransportInstruction.isProtocolVersionValid(inst)`-Pruefung wie im Client vor `inputReceiver.receive(inst)` einfuehren.
- Test fuer fehlende, falsche und korrekte `protocol_version` ergaenzen.

Validierungspfad:

1. Pruefen: `rg -n "isProtocolVersionValid|TransportInstruction.parse|inputReceiver.receive" mosh4j-core/src/main/java mosh4j-transport/src/main/java`.
2. Reproduzieren: eine `Transportinstruction.Instruction` ohne oder mit falscher `protocol_version` an den Server-Verarbeitungspfad geben.
3. Nach Fix: falsche Version muss verworfen werden; Version `2` muss weiter akzeptiert werden.

### L-2: GitHub Actions nutzen mutable Action-Tags, auch im Release-Workflow mit Schreibrechten

Beleg:

- `.github/workflows/release.yml:11-13` gibt `contents: write`.
- `.github/workflows/release.yml:27`, `:30`, `:52`, `:63`, `:70` referenzieren Actions per Tag, z.B. `actions/checkout@v4` und `softprops/action-gh-release@v2`.
- `.github/workflows/daily-snapshot.yml:22`, `:68`, `:73`, `:95` referenzieren Actions ebenfalls per Tag.

Bewertung:

GitHubs offizielle Hardening-Dokumentation beschreibt full-length commit SHA Pinning als einzige immutable Referenzform fuer Actions. Tags sind ueblich, aber beweglich. Besonders relevant ist das im Release-Workflow, weil dort `GITHUB_TOKEN` mit `contents: write` verwendet wird.

Empfehlung:

- Externe Actions auf full-length commit SHAs pinnen und den zugehoerigen lesbaren Tag als Kommentar dokumentieren.
- Optional eine Repo-/Org-Policy aktivieren, die SHA-Pinning verlangt.
- Bei Updates die SHAs ueber eine vertrauenswuerdige Update-Route erneuern.

Validierungspfad:

1. Pruefen: `rg -n "uses: .+@" .github/workflows`.
2. Nach Fix: jede externe `uses:`-Referenz muss auf `@[a-f0-9]{40}` enden.
3. Workflow-Regression: `workflow_dispatch` fuer Snapshot/Release-Testlauf ausfuehren.

### L-3: Maven Build- und Security-Gates sind nicht zentral erzwungen

Beleg:

- `pom.xml:15-20` definiert Java-Properties, aber im Root-POM ist keine `pluginManagement`-/Enforcer-Konfiguration vorhanden.
- `mosh4j-protocol/pom.xml:76-80` pinnt `maven-compiler-plugin` auf `3.13.0`.
- Beim ausgefuehrten `mvn test` wurde in anderen Modulen `compiler:3.15.0` aus der Maven-Defaultbindung verwendet. Das ist ein beobachteter Build-Output, keine POM-Zeile.
- `rg -n "(dependency-check|spotbugs|semgrep|codeql|snyk|trivy|osv|enforcer)" -S .` fand keine Repo-deklarierte SCA-/SAST-/Enforcer-Konfiguration.

Bewertung:

Das ist keine konkrete Runtime-Schwachstelle, aber eine Supply-Chain-Haertungsluecke. Der Bouncy-Castle-Befund waere durch ein CI-SCA-Gate eher frueh aufgefallen. Maven Enforcer kann Plugin-Versionen und Toolchain-Anforderungen kontrollieren; OWASP empfiehlt verwundbare Dependencies aktiv zu managen.

Empfehlung:

- Zentral `pluginManagement` fuer Maven-Plugins einfuehren.
- `maven-enforcer-plugin` nutzen, um Maven-/JDK-Versionen und Plugin-Versionen zu erzwingen.
- Ein SCA-Gate ergaenzen, z.B. OSV-Scanner, OWASP Dependency-Check/dep-scan oder eine GitHub-native Dependency-Scanning-Route, je nach Projektpolitik.
- Optional SBOM-Erzeugung in Release/Snapshot aufnehmen.

Validierungspfad:

1. Pruefen: `rg -n "<pluginManagement>|maven-enforcer-plugin|dependency-check|osv|codeql|semgrep" pom.xml */pom.xml .github/workflows`.
2. Nach Fix: `mvn -B validate` muss Enforcer-Regeln ausfuehren.
3. SCA-Reproduktion: absichtlich verwundbare Test-Dependency nur in einem separaten Testzweig einfuegen und pruefen, ob CI blockiert.

## Positive Befunde

- Session-Key-Generierung nutzt `SecureRandom`: `mosh4j-core/src/main/java/org/mosh4j/core/MoshServerMain.java:76-79`. Oracle/JDK dokumentiert `SecureRandom` als cryptographically strong RNG.
- Testserver redaktiert den Session-Key standardmaessig: `mosh4j-core/src/main/java/org/mosh4j/core/MoshServerMain.java:55-60`; README dokumentiert dies in `README.adoc:132-134`.
- Key-Material wird kopiert statt direkt offengelegt: `mosh4j-crypto/src/main/java/org/mosh4j/crypto/MoshKey.java:54-65`.
- AEAD-Tag-Fehler werden als `AEADBadTagException` behandelt: `mosh4j-crypto/src/main/java/org/mosh4j/crypto/SspCipher.java:58-74`; Tests decken falschen Nonce und manipulierten Ciphertext ab in `mosh4j-crypto/src/test/java/org/mosh4j/crypto/SspCipherTest.java:25-43`.
- ANSI-CSI-Parameter sind begrenzt: `mosh4j-terminal/src/main/java/org/mosh4j/terminal/MinimalAnsiParser.java:16-17`, `:62-84`.
- Port-Parsing im Testserver faellt auf Defaults zurueck und validiert Range: `mosh4j-core/src/main/java/org/mosh4j/core/MoshServerMain.java:23-43`.
- Offensichtliche Secret-Suche lieferte keine Treffer: `rg -n "(AKIA...|BEGIN PRIVATE KEY|MOSH_KEY=|password=|secret=|token=|api_key=)" -S .` endete ohne Treffer.

## Externe Referenzen

- OWASP Denial of Service Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html
- OWASP Vulnerable Dependency Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Vulnerable_Dependency_Management_Cheat_Sheet.html
- GitHub Actions Security Hardening: https://docs.github.com/en/actions/how-tos/security-for-github-actions/security-guides/security-hardening-for-github-actions
- Oracle Java 21 `SecureRandom`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html
- Protobuf Java `CodedInputStream`: https://protobuf.dev/reference/java/api-docs/com/google/protobuf/CodedInputStream.html
- Bouncy Castle CVE-2026-0636: https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%900636
- Bouncy Castle CVE-2026-5598: https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%905598
- OSV API: https://api.osv.dev/
- Maven Central Metadata fuer `bcprov-jdk18on`: https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml

## Offene Fragen vor Fix-Umsetzung

1. Welche maximale dekomprimierte Transport-Instruction-Groesse ist fuer reale mosh4j-Sessions akzeptabel?
2. Soll Replay-Schutz strikt monoton sein oder braucht das Projekt ein Fenster fuer legitime UDP-Reordering-/Retransmission-Faelle?
3. Gibt es bereits ausserhalb des Repos aktivierte GitHub-Sicherheitsfeatures wie Dependabot Alerts, CodeQL, branch protection oder Actions SHA-Pinning-Policies?
4. Soll `bcprov-jdk18on` direkt auf `1.84` angehoben werden, oder gibt es Kompatibilitaetsvorgaben fuer Downstream-Nutzer?
