# Paper — author's thoughts (verbatim) + working notes

> Rule of this file: **author's words are preserved verbatim** (diction, voice, framing).
> Claude's role = interlocutor/scribe. Questions and structure go in their own marked sections.
> When we shape the paper, it stays in the author's voice.

Working title (provisional): *Out-of-Band Computer Use — computers operating other computers.*

---

## §0 — Author voice, raw (verbatim, 2026-06-03)

Now, everyone is in the CUA game. Everyone is looking for a way to have ai play that multiplier
role for the human input that computer commonly interacts with. So much of legacy systems are
heavily reliant on the assumption that humans would use this tool or that tool, through a hardware
computer interface. but as in the way of natural evolution, human beings are migrating to a loftier
memetic elevation, and would like to make their computers operate other computers. this research
explores this possibility and explores the impediments to this happening.

first, is the possibility: this is indeed possible. the input that computers receive from peripheral
devices can be simulated; that's essentially what the HID-interface allows us to do. We can feed into
machine consumption, what is typically meant for a human audience. This doesn't come without costs or
tradeoffs, we either we use the visual understanding models (which collect params or don't) and send
the output as the input of an llm to decide on what to do next? …

[continues — author dictating; Claude asks questions below to draw out the full report]

---

## §V — Voice / diction guide (write the paper TO this)

Distilled from the author's own words. Approximate from a small sample; refine as more arrives.
- **Register:** elevated, essayistic, philosophical-meets-technical. Comfortable with large claims
  about evolution, memetics, and the trajectory of the human–computer relation. An engineer who
  philosophizes.
- **Rhythm:** long, flowing sentences; chained with commas, semicolons, colons, periods. Builds
  momentum. Comma-splices are fine. **NO EM DASHES — ever.** (Author rule.)
- **Person:** use **I** freely for stance and judgment; not shy about first person.
- **Move:** confident assertion, then qualification. ("this is indeed possible." → then the nuance.)
- **Pronouns:** inclusive **we**; occasional **I** for stance ("I am being vague right now").
- **Devices:** personification of the machine — it *consumes*, there is a *machine consumption* vs a
  *human audience*; the *multiplier* image; metaphor over jargon.
- **Hedges/asides (keep them):** "or thereabout," "in a generalistic sense," "the crux," soft
  qualifiers used deliberately.
- **Lexicon:** memetic, evolution, elevation, multiplier, lossy, consume, audience, peripheral,
  simulate, surface.
- **Argues in forks/questions** — poses the choice, then leans.
- **Avoid:** rigid academic scaffolding ("We propose three axes…", "In this section we…"), clinical
  hedging, bullet-itis where a flowing paragraph is truer to the voice.

## §A — Through-line Claude is hearing (to confirm, not to overwrite)

- Thesis seed: *the next evolutionary step is computers operating other computers* — AI as a
  **multiplier on the human-input channel** that legacy systems assume a human will drive.
- Possibility established via the **HID interface**: peripheral input can be simulated → we feed
  **machine consumption** what was authored for a **human audience**.
- Then: **costs/tradeoffs** — beginning with the perception+decision pipeline (vision-understanding
  model → LLM), which is where the dictation paused.

---

## §B — Q&A (Claude asks → author answers, verbatim)

### On vision params + the lossy nature of HID (re: Q1, Q2) — 2026-06-03
> On what params a vision model might consume: I am being vague right now, but rather than a black
> box vision where inference is performed on the frame or snapshot, there might be a context element
> we would like to pass as input to streamline the output and to trim unnecessary details or
> thereabout. I was speaking in a generalistic way. As for the Tradeoffs I said come with an
> HID-style interface, it's lossy in nature, even if we include params in the context of the vision
> model, we do not expect or assume we need 100% of our input back in the output.

_Claude read: perception need not be black-box — pass a **context element** to bias/streamline what
the vision step returns (trim irrelevant detail). And the pipeline is **lossy by design**: we don't
require a full round-trip of the input into the output — only what's needed to act. (Lossy as an
accepted property, not necessarily a defect.)_
