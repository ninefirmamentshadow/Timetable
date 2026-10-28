---
name: verify-repo
description: Verify this repository with scripts/verify.sh, fix what fails, re-run, and record the results in the branch handoff file. Use before every push, when asked whether the branch is green, or when picking up another agent's branch.
---

# verify-repo

Thin wrapper. The logic lives in `scripts/verify.sh` so a laptop, an agent and CI
all run the same thing. Do not re-implement checks here.

1. Read `.agent/handoffs/<branch with / replaced by ->.json` first if it exists.
   `claimed` is what the last agent said it did; only `verified` is evidence.
2. Run `scripts/verify.sh --handoff`. If the Android SDK is missing it prints
   `NOT_RUN` and the bootstrap command (`scripts/bootstrap-android-sdk.sh`); run
   that, export `ANDROID_HOME`, and run again. A step that did not run is
   reported as `NOT_RUN`, never as a pass.
3. On `FAIL`: fix the cause, re-run. A confirmed, reproducible defect gets a
   regression test in the same commit. Do not edit `lint-baseline.xml` to hide a
   new lint problem, and do not loosen a test to make it pass.
4. Golden screenshots (`app/src/test/screenshots/`) change only when the UI was
   meant to change: run `./gradlew recordRoborazziDebug`, review the images, and
   commit them with the UI change.
5. Update `claimed`, `pending` and `next_action` in the handoff file by hand;
   leave `verified` to the script. Real-device install and acceptance stay in
   `not_run` until the owner has tested on the phone.
6. Report PASS, FAIL and NOT_RUN per step, with the HEAD SHA the script printed.
