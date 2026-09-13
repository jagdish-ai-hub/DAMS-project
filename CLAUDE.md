# CLAUDE.md

Sources of truth, in order — read all three before writing code:

1. **AGENT.md** — the project spec. If a rule there conflicts with something
   convenient to build, AGENT.md wins. Change AGENT.md first, then build.
2. **plan.md** — build plan: locked decisions, entity list, Flyway migration
   plan, API endpoints, stage-by-stage build order. Locked unless AGENT.md
   is updated first.
3. **intial ui prototypes/*.html** — layout, flow, field names, and
   interactions for the frontend. Build to match these closely, not as loose
   inspiration. `dams-er-diagram-v2.mermaid` is an early sketch; where it and
   plan.md disagree, plan.md is newer.

## Working process (from AGENT.md)

Before writing code for a new stage or feature, propose the plan in plain
language first — entities touched, migrations, endpoints, screens — and wait
for confirmation. Don't treat a prior stage as untouchable if new
requirements mean it should change; flag the conflict and ask.

## Answer first, then work

When the user asks a question — or their message contains a question mixed
with a request — answer the question directly and up front, in plain words,
BEFORE running tools or writing code. Do not bury the answer at the end of a
long work turn; the user must not be left guessing what you're doing.
State what you're about to do in one line, then do it.

## Never say "fixed" without checking the layer the user sees

Reading the source and passing a test proves *intent*, not *effect*. Several
bugs in this project were declared fixed two or three times while still fully
broken in the user's browser (see plan.md revs 39, 40, 41). Before using the
word "fixed", verify where the user actually looks:

- **Frontend/CSS** — grep the built bundle, not the source:
  `npm run build && grep -o 'Toggle navigation.\{0,200\}' dist/assets/*.js`,
  and check the emitted CSS rule/media query. An inline `style` prop beats
  every stylesheet class, so a correct-looking `className` can be a dead
  no-op (this is exactly what hid the hamburger bug for three rounds).
- **Data/workflow** — query the live Neon DB (MCP `run_sql`) for the actual
  row the user is describing, rather than reasoning about what the code
  should have written.
- **Behavior** — trace the whole path the user takes, not the one file that
  was changed.

## A bug report is ground truth

When the user says something is broken, it is broken — find the mechanism.
Do not offer browser cache, deployment, or "that's by design" as an
explanation before proving it, and never in a way that implies the user
misread their own screen. Cache and spec have both been wrong here while the
user was right.

If the behavior really does match the spec but the user's goal is reasonable,
the **spec** is the thing that's wrong: say so, change AGENT.md first, then
build (see "Working process").

## Fix the rule, not the screen

When a fix changes a rule (e.g. "a claim line may be ₹0"), grep for every
place that rule is enforced — backend DTO validation, service checks, and
each frontend screen — and fix all of them in the same change. Fixing only
the screen the user pointed at is how the same bug gets reported again.

## Stack is fixed

See AGENT.md "Tech stack" and plan.md "Tech Stack". Do not substitute
libraries or versions without asking.
