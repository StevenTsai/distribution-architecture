# ADR: Zero Build Chain First on the Client — JavaScript over TypeScript, Homegrown Store over a State Library

[中文](../../decisions/why-javascript-not-typescript.md) | English

> Status: Accepted (costs already realized and recorded)
> Date: 2026-09 (planned TypeScript + mobx) → 2026-10 (converged to zero build chain)
> Decision Maker: Architecture Team

---

## Background

The client is a WeChat Mini Program (native framework). The planned stack was **TypeScript + mobx-miniprogram + bindings**—type safety, mature state library.

Implementation converged to **plain JavaScript (CommonJS) + a homegrown `store.js` (about 18 lines)**, with no build tools and zero npm runtime dependencies across the repository. The driving constraint was singular: **the edit-preview feedback loop in the Mini Program dev tools must stay sub-second, and any build chain inserts a compile step on every save**.

This is a **deliberate choice to give up one layer of type protection**. Per this repository's discipline, we record faithfully: the rationale, the costs already paid, and the revised root-cause judgment after those costs came due.

## Decision

1. **JavaScript (CommonJS), not TypeScript**—no compile step; the artifact is the source;
2. **Homegrown `utils/store.js` (`get` / `set` / `subscribe`, ~18 lines), not mobx-miniprogram**—`set` does `Object.assign` and notifies all subscribers wholesale.

Both are two applications of one principle: **zero build chain first**. Pulling mobx-miniprogram via npm normally implies a bundler, conflicting with the principle; the homegrown store compresses the state-library requirement into 18 auditable lines.

## Alternatives Considered

### Option A: TypeScript

| Dimension | TypeScript | JavaScript (this project) |
|-----------|------------|---------------------------|
| Protocol numeric-op protection | Compile-time type checks | None; review and tests only |
| Build chain | Mandatory (compile + sourcemaps) | None |
| Edit-preview loop | Extra compile on every save | Edit and preview immediately |
| Artifact readability | Artifact ≠ source; needs sourcemaps | Artifact is the source |
| Mini Program toolchain fit | Requires custom compile config | Native support |

### Option B: mobx-miniprogram + bindings

| Dimension | mobx | Homegrown store (this project) |
|---|---|---|
| Integration cost | npm + typically a bundler | 18 self-contained lines |
| Capability surface | Deep observable tracking, computed, actions | `get` / `set` (broadcast) / `subscribe` |
| Fit for state-tree scale | Any | Shallow tree, ≤16 pages—sufficient |
| Mental overhead | Observable semantics and pitfalls | No new concepts |

**The real constraint against TypeScript was not "dislikes types"**: given the native Mini Program framework and the zero-build-chain requirement, TS's benefit (compile-time checks) must be paid for with a compile step—and that step lands directly on the edit-preview loop. For a protocol-integration-heavy workflow, that is a tax paid dozens of times a day.

## Why Zero Build Chain

1. **The feedback loop is the dominant cost of Mini Program development**. The integration workflow is "edit one frame parser → save → check on device/emulator," each cycle counted in seconds. A build chain adds a fixed compile to every cycle—over weeks of endpoint integration, this tax exceeds the value of type checks.

2. **Artifact-is-source = frictionless on-device debugging**. Production issues are read straight from vConsole / dev tools as source, no sourcemap mapping. For a project that reconciles protocol parsing frame-by-frame on real devices, this daily value is chronically underestimated.

3. **The homegrown store's 18 lines are the entire mental overhead**:

   ```js
   // utils/store.js — the whole implementation
   const state = {}
   const subs = []
   function get(key) { return state[key] }
   function set(key, value) {
     Object.assign(state, { [key]: value })
     subs.forEach((fn) => fn(key, state))
   }
   function subscribe(fn) { subs.push(fn) }
   module.exports = { get, set, subscribe }
   ```

   No observable tracking, no computed, no actions—**when the state tree is shallow (≤16 pages), an over-capable state library only adds semantic overhead**.

4. **The performance gap is closed by calling convention, at an identifiable location**. Wholesale notification means 1 s telemetry pushes the entire data tree; throttling narrows to the caller-side `setIfChanged` (field-by-field comparison before `setData`). This is a textbook **calling-convention compensation for missing framework capability**—every trade-off point sits in one utility function, auditable and testable, instead of being scattered through a state library's proxy semantics.

## Trade-off

### Costs (realized, not hypothetical)

- **Two protocol numeric-op defects have already reached production paths**:
  1. Sign-extension error when reading a 4-byte field with 16-bit signed correction;
  2. Digest computation error from missing zero-padding after bit-shift concatenation.

  Both belong to "fixed-length integer arithmetic"; TypeScript's annotations **could possibly** have caught the first at compile time (if field widths entered the type system).

- **The broadcast notification performance model depends on caller discipline**. Forgetting `setIfChanged` and calling `set` on telemetry data does not error—it stutters, and the defect is hard to reproduce stably in the test environment.
  - Mitigation: `setIfChanged` is the sole entry point on telemetry paths, gated by code review; on-device performance sampling is part of the release checklist.

- **No compile-time contract checking**. Frontend/backend field-contract drift surfaces only during joint debugging.
  - Mitigation: contracts are pinched from both sides by a zero-dependency Node test suite (real server implementation × real client implementation over real HTTP), so drift surfaces in CI, not on-device.

### Root-Cause Revision After Costs Came Due (the most important section)

Both defects were first attributed simply to "the cost of not using TypeScript." The post-mortem judgment: **the root cause was a missing unified fixed-length-integer utility layer in the protocol code** (sign correction, bit-shift, and zero-padding each implemented ad hoc inside business code); the type system was only one possible means of surfacing it earlier.

Corollary: even with TypeScript at the time, the same defect class would still have appeared in another shape (types correct, formula wrong) as long as the utility layer was missing. **The correct remediation order is utility layer first, types second**—sign correction and zero-padding have since been consolidated into shared protocol-layer utility functions.

### Benefits

- Sub-second edit-preview loop; zero compile waits during protocol integration;
- On-device debugging reads source directly;
- The entire mental overhead of state management = 18 lines + one throttle function; a newcomer finishes reading in ten minutes;
- Zero npm runtime dependencies: `git clone` runs it—no `node_modules` surface.

## Future Evolution

1. **Type defense without adding TS**: ESLint rules plus shared protocol-layer utilities take over the duties the type system would have carried (static checks on magic numbers and hard-coded bit widths);
2. **If the state tree deepens or page count grows**: the homegrown store's broadcast model hits its limit first—the upgrade path is "add fine-grained notifications" or introduce a state library while **accepting the build chain in the same breath**; the two are bundled, never split;
3. **Re-evaluation trigger for this ADR**: a second defect class that "would have been caught at compile time" appears **after** the utility layer and static checks are in place—only then is TypeScript's benefit worth repricing.

---

See also: [Spring Boot Single Track over NestJS](why-spring-boot-not-nestjs.md) · [State Storage: Redis Optional](why-redis-optional.md)
