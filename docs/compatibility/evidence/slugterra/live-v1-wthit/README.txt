Slugterra v1.0 WTHIT coexistence audit — 2026-09-30

Artifact: slugterra-1.0.jar
SHA-256: b2388d688b2902a5931a3c8be44a997fdc87789a192c31a64599b2eda591c736
Matches GitHub v1.0 release asset digest.
Magnetization source: c2a9072, unchanged.
Minecraft 1.21.1; NeoForge 21.1.252; Sable 2.0.5; GeckoLib 4.8.4;
WTHIT 12.10.2; Bad Packets 0.8.2. Dedicated loopback server and two real clients
on private Xvfb displays. Synthetic offline player profiles.

PASS: Visually inspected all ten HUD screenshots (five states on each client).
- Normal Rammstone: Magnetized III, finite countdown and pinning explanation
  coexist with Slugterra combat/trust levels.
- Natural expiration removes all magnetic lines while combat/trust remain.
- Dark Rammstone: same coexistence with finite magnetic effect.
- Infinite magnetic effect displays infinity and pinning correctly.
- Explicit removal clears all magnetic lines while combat/trust remain.
No duplicated magnetic lines or displaced native combat/trust fields observed.
Line ordering differs between clients but content is correct.
Owner ??? reflects synthetic offline test profiles; owner-name lookup not tested.
The fixtures had no food craving; no craving line was expected in these captures.

NOT COMPLETED: A follow-up explicit food-craving fixture and full 64-case flight
rerun were interrupted by test-process termination. No complete v1.0 multiplayer
flight pass is claimed. Retained logs show a partial flight run through case 26;
this is not a substitute for the completed audit on the older artifact.
The terminal session reported termination (143); no cause is established.

Previously completed v1.0 server regression: all 9 integration GameTests passed
with Sable 2.0.5. Sable 2.0.3 is rejected by Slugterra v1.0's version constraint.

No production code changes were made for this verification.
