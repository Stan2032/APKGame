# Multiplayer architecture — PVP without servers

## The insight

The simulation is bit-for-bit deterministic (see DETERMINISM.md). Therefore a
complete match is fully described by:

```
seed (8 bytes) + per-turn quantized inputs (8 bytes each) + verification hash (8 bytes)
```

That's the entire netcode. 26 bytes of frame + 10 bytes per shot + 5 per
anchor, base64url
encoded with a CRC — a 20-turn duel is ≈ 230 characters of text.

## Transport: any messenger the players already use

Codes travel over WhatsApp/Signal/SMS/Discord/email — whatever the players
have. Consequences:

- **Zero server cost** — the user asked for cheap; this is free.
- **Encryption for free** — the code rides the players' own (E2E-encrypted,
  if they chose such a messenger) channel. We add no network surface at all:
  our code contains no networking and the game is airplane-mode-complete.
  (The merged manifest does carry INTERNET via Play Billing's transitive
  datatransport component — see DECISIONS.md D-3; we neither use nor need it.)
- **No accounts, no matchmaking cold-start** — your opponent is anyone you
  can text. A brand-new game with zero install base has working PVP on day one.

## Anti-cheat: the physics is the referee

`ShareCodec.decodeAndVerify()` on the receiving device:

1. Decodes and CRC-checks the payload (transport corruption → reject).
2. Regenerates the star system from the seed (identical by determinism).
3. Re-simulates every turn through the same `MatchEngine`.
4. Compares the resulting 64-bit trajectory/outcome hash against the claimed
   hash. Mismatch → reject with "the physics noticed."

The declaration a player made rides inside that hash too, so a finished shot
cannot be re-declared after the fact to claim a bigger payout (docs/VISION.md
§3.2). This is also the reason gambits are limited to constraints the flight
itself reveals — see "What can and cannot be a scored rule" below.

A cheater cannot claim a hit that didn't happen: the only way to produce a
valid code is to actually find inputs whose *simulation* produces the claimed
outcome — which is just called "playing well." The class of remaining attacks
(solver-assisted aiming, i.e. aimbotting offline) is inherent to *any*
turn-based game, including chess-by-mail; mitigations (shot clocks in
realtime mode, statistical anomaly detection) are roadmap items, and the
threat model is documented honestly here rather than hand-waved.

### What can and cannot be a scored rule

Everything the receiving device knows, it derives by re-running the physics.
That draws a hard line around what may carry score. A **gambit** (declared
constraint, docs/VISION.md §3.2) is judged from the rebuilt flight — power
used, bodies passed, path relative to the launch→target line — so the peer
reaches the same verdict independently, and the declaration itself is folded
into the shot hash, so it cannot be edited after the fact to claim a payout.

A constraint the simulation cannot observe cannot be a scored rule here, no
matter how good an idea it is. "Fire with no aim preview" is the example: it
would have to be believed. It was cut for that reason alone (D-23).

## Protocol details

`GG1.` prefix + base64url(payload):

| Field | Size | Notes |
|---|---|---|
| magic "GG" | 2 | |
| version | 1 | currently 1; strict check |
| mode | 1 | 0 campaign / 1 daily / 2 duel |
| difficulty | 1 | generator band |
| seed | 8 | big-endian |
| turnCount | 1 | ≤ 64 |
| turns | 8×n | shooter, angleDeci(0..3599), power(0..1000), typeId, brakeTick |
| hash | 8 | combined FNV-1a over per-shot trajectory hashes + rule events |
| crc32 | 4 | over everything above |

Decode is tolerant: it finds `GG1.` anywhere in pasted text, so codes survive
being embedded in chat messages. All fields are range-validated; anything
out of range or failing CRC/hash → null (never a crash, never a partial state).

## Turn flow (code duel)

1. Player A creates a duel → seed chosen → A shoots → app emits code(turns=1).
2. B pastes the code → verification → **B's phone replays A's shot as a
   cinematic** → B shoots → code(turns=2) goes back.
3. Repeat until a station dies (or 30-turn cap). The final code carries the
   full match; either side can archive/verify it forever.

Turn ownership is structural: `localPlayer = turns.size % 2`. No state beyond
the latest code is required — the code *is* the save file. The app remembers
your last outgoing code for convenience only.

## Phase 2: realtime P2P (designed for, not yet built)

WebRTC data channels + deterministic lockstep: each frame exchanges only
quantized inputs; the engine already treats inputs as the sole source of
truth, so the netcode drops in without touching game logic. Needs a minimal
signaling endpoint (free-tier Cloudflare Worker / Firebase) used only for the
handshake; gameplay traffic stays peer-to-peer (DTLS-encrypted by the WebRTC
standard). This will add the INTERNET permission and a Data-Safety update —
deliberately deferred so v1 ships with the cleanest possible privacy posture.
