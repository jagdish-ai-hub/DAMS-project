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

## Stack is fixed

See AGENT.md "Tech stack" and plan.md "Tech Stack". Do not substitute
libraries or versions without asking.
