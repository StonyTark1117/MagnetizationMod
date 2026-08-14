# Immutable playtest baselines

These images are release evidence, not an automatically seeded cache. Every
station declared in `playtest/automation-matrix.json` must have a committed PNG.
Ordinary automation runs compare against these files and fail when one is
missing. Replacing an accepted image requires an explicit review run, for
example:

```sh
scripts/run-playtest-automation.sh lab record
```

`./gradlew verifyPlaytestBaselines` checks manifest coverage without launching
Minecraft and validates every PNG against `SHA256SUMS`, so an unreviewed image
replacement cannot silently become release evidence. Baselines are captured at
the fixed 854x480 client window configured by `playtest/options.txt`; optional
HUD profiles retain separate `jade/` and `top/` directories so one integration
cannot stand in for another.
