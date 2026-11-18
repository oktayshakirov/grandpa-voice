#!/bin/sh
# One deliberate shake: 5 hard pushes (~3 g) about 250 ms apart, then back to resting.
for i in 1 2 3 4 5; do
  adb emu sensor set acceleration 28:4:9.8 >/dev/null
  sleep 0.12
  adb emu sensor set acceleration 0:0:9.8 >/dev/null
  sleep 0.12
done
adb emu sensor set acceleration 0:9.8:0 >/dev/null
