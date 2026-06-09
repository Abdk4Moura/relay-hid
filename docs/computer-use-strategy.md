# Relay → Money: computer-use / agent-execution strategy (context doc)

> Saved 2026-06-03 to regain context later. Personal/private — not for the public repo.
> Situation: income is urgent; **prioritize fastest-to-cash paths (job/contract > product).**

## The asset (what's already built)
- **Cross-platform desktop receiver** (Rust) that injects real keyboard/mouse into a live OS — **Linux (uinput), Windows (SendInput), macOS (CGEvent via enigo)**. Verified: Linux injection in CI; Windows live end-to-end; macOS transport proven live.
- **`moveto` absolute-pixel protocol** — built specifically so a *vision model* can hit absolute screen targets (the exact thing computer-use agents need).
- **Bluetooth-HID out-of-band control** — the Android app can *be* a physical keyboard/mouse to any host. This controls machines with **no software installed, no permissions, that can't be virtualized** (kiosks, locked-down/regulated boxes, BIOS, anything with USB/BT).
- Polished **Android app** (Kotlin/Compose) + one-line installers + CI release pipeline + a `livetest` workflow that drives real Win/Mac runners over Tailscale.

## Market research (2026) — the landscape
- **Frontier labs all ship "computer use" but it's mediocre at execution.** Anthropic Computer Use (paid beta, Opus 4.8), OpenAI Operator/CUA + Codex Computer Use (Windows support May 29 2026), Google, Microsoft. **Operator scores ~32.6% on OSWorld** (fails ~2/3 of real tasks), mostly browser-bound. Models race ahead of the execution layer.
- **Infra startups = where money moves**, almost all **cloud VMs/browsers**: **E2B** (~$32M, "cloud computer for every agent", 94% of Fortune 100), **Browserbase** (~$300M valuation <2yr), **Scrapybara**, Microsoft **MXC** (OS-level agent sandbox, OpenAI+Nvidia onboard).
- **Closest to our tech is OSS: `trycua/cua`** — infra for computer-use agents controlling full desktops (macOS/Linux/Windows), Rust+Swift+Python, background "CUA Driver" + VM sandboxes. **Validates the skill is exactly what this sector pays for** (not an untapped product moat solo).

## Where WE are differentiated (the gap)
- Everyone above drives **cloud VMs/browsers** or needs **software on the target**.
- **Our BT-HID is out-of-band physical control** — no install, no permissions, works on un-virtualizable / on-prem / regulated machines. Research flags the real unmet need: **on-prem / regulated industries** (finance, healthcare, gov) that *can't* use cloud sandboxes. **This is our distinctive angle.**

## The play (fastest money)
**Sell time, not product.** Relay = portfolio + live demo. Use it as a **demo-first proposal** to get hired/contracted in the computer-use / agent-execution space — a hot, funded sector hiring for *exactly* these skills, where most applicants can't show a working cross-platform execution layer.

### Target companies / roles
- **Minicor** — "RPA into legacy desktop systems," hiring **Forward-Deployed Engineers** (bullseye).
- **CoPlane** (agentic orchestration), **E2B**, **Browserbase**, **Scrapybara**, **trycua/cua**.
- RPA-into-legacy shops; **HN "Who's hiring (June 2026)"**; **YC remote SWE jobs**; agent-infra startups broadly.
- Angle for outreach: "I built the execution layer your agents need — incl. out-of-band BT-HID control real sandboxes can't do. Here's a 60-sec demo."

### Deliverables to build when we resume (in order)
1. **One-page positioning** (demo-first) — every other step reuses it.
2. **Target list** — specific companies + open roles + where to apply.
3. **Cold-outreach drafts** — to founders / eng-leads, demo-first.
4. **60-sec demo recording** (Loom/GIF): phone → Tailscale → real Win/Mac typing; + the `moveto`/agent angle.
5. Résumé/LinkedIn bullets translating this work for hiring scanners.

## Sources
Anthropic Computer Use docs · OpenAI Operator/CUA · digitalapplied computer-use-agents-2026 · E2B & Browserbase (jimmysong.io) · e2b.dev · browserbase.com/computer-use · github.com/trycua/cua · VentureBeat Microsoft MXC · Northflank on-prem sandboxes · hnhiring.com/june-2026
