# Computers Operating Computers: Out-of-Band Computer Use Through the Human Interface

**Advanced draft. Written to the author's diction (see `paper-notes.md` §V): no em dashes, first
person used freely. Italic phrases are the author's, verbatim. `[A?]` marks a judgment call to confirm
or overrule. The lyrical register is a placeholder; the author will set the final taste.**

---

## Abstract

Every machine we have ever built quietly assumes that a human will sit in front of it. The keyboard,
the mouse, the screen: these are not incidental, they are the contract, and almost the whole of our
legacy software rests on the premise that some person will perceive its output and supply its input.
The present wave of *computer-use agents* wants to slide an artificial intelligence into that very
seat, to have it play the *multiplier on the human-input channel* that our systems have always assumed
a human would occupy. I hold that this is not a feature but a turn in the road, *human beings migrating
to a loftier memetic elevation, where they would have their computers operate other computers*, and
this paper looks at both the possibility and the impediments.

The possibility is real, and its mechanism is almost dull. The input a computer takes from its
peripherals can be *simulated*, which is, in a generalistic sense, exactly what the HID interface was
built to permit. From that one fact follows everything, because we can now *feed into machine
consumption what was authored for a human audience*: pixels meant for eyes, controls meant for hands. I
will argue that this channel is **lossy in nature**, and, against the instinct to treat that as a
wound, that the loss is the very thing that makes it general. An agent that works the surface as a
human works it does not need the machine's inner truth; it needs only enough of the surface to choose
its next act, and *it neither expects nor requires the whole of its input to return in its output*.
From there I draw an **out-of-band** body, HID for the hands and video for the eyes, that asks
*nothing* of the target, and so reaches the machines the in-band, sandboxed agents cannot touch at all:
the locked-down, the air-gapped, the regulated, the pre-boot, and the things that were never quite
computers. I describe a working, cross-platform system, set it beside the field as it stands, and name
the constraints that still bind.

---

## 1. The turn in the road

For fifty years the interface contract of computing has been a human one, and so total that we forget
to say it. Applications, operating systems, kiosks, terminals, the little control panels bolted to
industrial machines: every one of them was authored on the assumption that a person would stand in
front and drive it. Call it the *human-audience assumption*. It is the water the systems swim in.

Computer-use agents turn the assumption over. They do not ask a human to work the tool, they ask a
model to. Everyone is in this game now. The frontier labs have all shipped their version, and beneath
them a layer of infrastructure has formed to rent the agents a place to run. And yet, for all the
motion, the published competence is thin. On the standard benchmark the best agents finish only a
minority of ordinary tasks. The reading I take from this is simple: *the models are running ahead of
the means of execution.* We can decide; we cannot yet reliably *act*.

So the claim of this paper is that *computers operating computers* is the layer now coming into being,
and that the half of it which has gone under-examined is the **execution**: not what the agent ought to
do, but how the act actually lands on a real machine, out where the wires are. `[A? — I made the
evolutionary line the headline and out-of-band execution the contribution. Flip if you would rather
lead with the substrate.]`

I offer three things. First, a way of *seeing* the whole problem, as the *consumption, by a machine, of
an interface authored for a human audience*, with the argument that this consumption is **lossy by
design** and that the loss is a gift rather than a defect. Second, an **out-of-band** body, HID and
video, that works entirely through the human interface and so installs *nothing* on the target, and the
reach this buys. Third, a working system, and an honest accounting of what still gets in the way.

---

## 2. That it is possible

The possibility leans on a single fact about how machines are made. A computer does not, and cannot,
tell apart a key struck by a finger and a key handed to it as a HID report. Down at its input stack
they are one and the same event. The HID specification, the same on every operating system and alive in
firmware *before any operating system has even loaded*, exists for precisely this: to let some outside
thing speak the native tongue of a keyboard or a mouse. To synthesize input, then, is not to break the
machine. It is to use it as it was drawn.

And the mirror of it: the output can be *watched*. The video a machine throws to its display is a
faithful portrait of its state, and it is there at the cable no matter what software is, or is not,
running. Between the two, input we can simulate and output we can observe, sits the whole loop of
computer use, and it is an old and human loop: *see the surface, choose an act, deliver the act, see
again.*

The novelty was never the loop. It is the *audience*. The surface we read and the controls we drive
were composed for a person, and what we are doing, this is the crux, is *feeding into machine
consumption what was meant for a human audience.* Everything generous about the method, and everything
it costs, falls out of that one inversion.

---

## 3. Lossy by design

The tempting thing is to hand the agent the machine's *inner* truth, the accessibility tree, the
document model, the window manager's geometry, so that it acts on facts and not on a picture. This is
the **in-band** posture, and where you can have it, it is precise. But you can only have it when the
target consents: when you may install your software, hold your permissions, and address a program
willing to expose its bones.

The **out-of-band** posture refuses that dependence, and pays for the refusal in fidelity. A model
reading pixels meant for human eyes recovers less than an API would simply tell it; the channel is
*lossy in nature*. I want to argue that this is the right bargain, and for the reason the whole thing
turns on: *even with a context given to the perception step, we neither expect nor assume we need the
full of our input back in our output.* The loop is a compression and was always meant to be. A person
sitting down at an unfamiliar machine does not memorize the screen. They pull the one affordance they
need and let the rest fall away. The out-of-band agent is that behavior, mechanized, and so it inherits
the property that made human operation universal in the first place: it needs nothing *of* the machine
beyond the surface the machine already shows to anyone who looks.

Two things follow. First, the eyes need not be a black box. Rather than inferring blind over a raw
frame, perception can be *narrowed by a context element*, a goal, a region to attend to, a memory of
where we just were, so that the step returns *less, but the right less*. We are not after the most
information; we are after enough for the next act, or thereabout.

Second, and this is the lever for everything below: *fidelity is not the bottleneck.* If we never
wanted the whole input back, then whatever binds this system binds it somewhere else. It binds in the
*grounding*, in the *latency*, in the curious one-handedness of a channel that can only write, and not
in how much of the screen a model can reconstruct. `[A? — I framed the loss as a property to embrace.
If you also want the cost-tension alive, I will thread it back in.]`

---

## 4. Where the body attaches

It helps to separate the agent's *brain*, the thing that sees and decides, from its *body*, the means
by which it sees and acts at all. And once separated, you notice the body can be joined to a target in
two ways that are different not in degree but in kind.

| | **In-band** (a sandbox, or installed software) | **Out-of-band** (this work) |
|---|---|---|
| Eyes | a screen-capture call, run *inside* the target | the display's own **video**, captured (HDMI to capture) |
| Hands | an input-injection call, run *inside* the target | **HID**: the target merely sees a keyboard and mouse |
| Installed on the target | required | *nothing* |
| Permissions on the target | required | *none* |
| Target OS must be up and willing | yes | *no*; it works at the BIOS, locked, even crashed |
| Reaches the things that aren't quite PCs (kiosks, HMIs, ATMs, TVs) | no | *yes* |

The out-of-band body is, said plainly, *an intelligence seated where a KVM sits*: the
keyboard/video/mouse position from which an operator has always run a server even with its operating
system dead on the floor. Where the in-band agent drives machines it was *let into*, the out-of-band
agent works any machine the way a person physically would, through the screen it shows and the ports it
leaves open. So the set of machines it can touch is a *superset* of the in-band set, and the difference
between them is exactly the population that matters most out in the world: the machines one is
forbidden, or simply unable, to install anything upon.

---

## 5. Seeing, deciding, and the act of grounding

The brain arranges itself in one of two ways, and the author puts the choice as a fork. Either a
*vision-understanding* model reads the frame and feeds its reading, as input, to a *language* model
that chooses the act, or a single, *end-to-end* multimodal model that sees and chooses in one breath.
`[A? — you paused before naming the second arm; I supplied "end-to-end multimodal." Correct me if you
meant otherwise.]`

Either way the decision has to be *grounded*. The affordance the model named, "the OK button," "the
field," has to become a real act on the surface, which is to say, most often, a coordinate. And here
the out-of-band body pays its fidelity tax in the open. Two small problems keep recurring. The model
speaks in *absolute* targets, and a mouse moves in *relative* ones, so I close the gap with an absolute
path, a virtual pointer that spans the screen, and a relative fallback (pin to a corner, then step in
by the exact offset) for the channels, the Bluetooth ones especially, that will only ever move
relatively. And because the target cannot tell our synthetic hand from a living one, the *manner* of
the act can be made human, an eased and slightly curved motion rather than a teleport, which earns its
keep both against systems that sniff for non-human input and for the simple legibility of the agent to
whoever is watching over it.

Then the act goes down the body of §4, the surface changes, a fresh look closes the loop, and we go
again.

---

## 6. What still gets in the way

Having made our peace with the loss, what is it that actually binds the thing? I will name the
constraints I take to be load-bearing, and you should tell me which of them you want to carry the
paper. `[A?]`

The first is *grounding*. Turning a lossy, human-surface glance into the *right* coordinate is the
dominant way these agents fail; it is most of why the benchmarks read low. Out-of-band takes that on
whole, and adds the small sins of its own capture, a little sampling error, a little scaling.

The second is the *one-handedness* of the channel. HID only writes; it reads nothing back. Every fact
about the world, including whether the last act even worked, has to come home through the eyes, which
makes the loop strictly observation-bound and makes every verification cost a fresh look.

The third is plainer: *time and money*. Each turn buys a fresh observation and a model call.
Out-of-band can shave some of it, because capture is continuous and cheap, but the per-act latency is,
in the end, the model's to spend.

The fourth is the deep one, and it is the one from §2 wearing different clothes. The surface was
*authored for a human audience.* A machine consuming it works one step removed from the system's real
intent, and no cleverness ever quite shuts that gap. It can only be made *acceptable*.

And the fifth is consent. A channel that drives any machine as a human would is also the channel an
adversary would dearly want; the threat model is the threat model of KVM-over-IP, no more and no less.
Because we work *out-of-band*, the target's own software cannot stand at the door and check us, so the
checking has to happen at the physical layer, or the organizational one.

---

## 7. A body I built

To keep the argument honest I built a working one. `[A? — keep as concrete evidence; trim if the paper
should stay pure argument.]`

The hands, for the in-band path, are a small cross-platform service that lays down synthesized input on
Linux (the kernel's `uinput`), Windows (`SendInput`), and macOS (`CGEvent`), and speaks a thin
line-of-JSON protocol: move-to, move, click, key, text, scroll. The Linux path I check end-to-end in
continuous integration, by reading the very events back off the kernel device. Windows I drove live;
macOS I proved over a network tunnel. The hands for the *out-of-band* path are a phone, or a small
dongle, presenting itself as an ordinary Bluetooth keyboard and mouse to a target that has had nothing
done to it. The eyes are pluggable: the target's own display when I am allowed in, or an HDMI capture
card for the true, no-software, *KVM-brain* arrangement.

The brain I kept deliberately loose. The execution layer cares nothing for which model decides. I drove
it from Anthropic's and Gemini's computer-use loops, and, to show the seam is clean, I exposed the acts
as Model-Context-Protocol *tools*, so that any agent that speaks MCP gains a body on a real machine,
with its own model and no second key.

---

## 8. How I would measure it

Three axes, of which the first is the one this paper is really about. `[A? — confirm the eval you want
to stand behind.]`

The honest measure is *reach*. Lay out the kinds of target: the cloud VM, the willing desktop app, the
managed and locked-down PC, the air-gapped box, the BIOS or recovery screen, the kiosk and the HMI and
the ATM, the television. Then mark, plainly, which an in-band agent can operate *at all* and which an
out-of-band one can. The thesis makes a flat prediction: out-of-band strictly dominates on reach, and
pays for it in precision. The second axis is *task success*: ordinary tasks run through the out-of-band
body across the three desktops, read against the in-band ceiling. And the third is *the price of the
loss*: the same tasks done in-band, with structure, and out-of-band, with only pixels, and the gap
between them named in numbers.

---

## 9. Where it goes

A *talking* body. A live, streaming voice-and-vision interface does nothing for grounding, but it opens
a different door: a person narrating their intent aloud while the agent watches a live surface and
acts, over an out-of-band body, on a machine it has no foothold in. That is a striking thing to be able
to demonstrate.

A *hybrid* brain. Let a streaming model hold the plan and the awareness, and hand the precise
coordinate to a grounding step, a tuned model or a marked-up frame, and you buy the precision back
without surrendering the reach.

A *form*. The cleanest out-of-band body is one device: HDMI in for the eyes, a USB gadget that pretends
to be a keyboard and mouse for the hands, an *AI-KVM*. That is the shape the argument wants to take when
it becomes a thing you can hold.

And a *place to land*. The reach is worth most exactly where the in-band way is forbidden: in finance,
in healthcare, in government, on the floor of a plant. Which is to say, where the work actually is.

---

## 10. The neighbours

The frontier computer-use systems and the benchmark they are scored on; the purpose-built grounding
models; the open infrastructure for desktop agents; the cloud sandboxes and the browser farms; the new
OS-level sandboxes; and, older than all of it, the long lineage of robotic process automation that has
been poking at legacy desktops for years. The line I draw cuts across all of them, and it is not about
*which model*. It is about *where the body attaches*: inside the target, or out at the human interface
it was always assumed a human would hold.

---

## 11. In closing

The move from humans working computers to *computers working computers* asks for no new physics. It
asks only that we let a machine consume what we already make for human eyes and hands, and that we make
our peace with the loss that consuming entails. Done in-band, it is precise, and bounded to the
machines we are let inside. Done out-of-band, through the very seat the human was assumed to fill, it is
lossier, and unbounded in its reach, working any machine with a screen and a port the way a person
would. The loss, I have argued, is not the wound but the wing. The constraints that remain are real and
named, and a working body for the idea exists. The frontier is no longer *whether* one computer can
operate another. It is how cheaply, how exactly, and, the question that will decide who this is for, on
*which* machines.

---

### Open decisions (carried from the draft)
- Resolve each `[A?]`.
- §1: evolutionary claim as headline, or the substrate?
- §3: loss purely as gift, or keep the cost-tension?
- §6: which constraint carries the paper?
- §5: confirm the second arm of the fork.
- §7–8: how much system/measurement to keep vs. pure argument.
