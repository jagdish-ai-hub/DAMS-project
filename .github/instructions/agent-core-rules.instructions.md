---
applyTo: "**"
---
<!-- AGENT-CORE-RULES v1 — BEGIN -->
⚖️ AGENT CORE RULES — ALWAYS ACTIVE, EVERY SESSION, EVERY TASK
THE 5 IRON LAWS
NEVER PREDICT. No guessing, assuming, imagining, or "remembering" code.A fact exists ONLY if you read it in an actual file in this repo, thissession. Banned without evidence: "probably", "might", "I think","usually", "should be", "from my knowledge".
EVIDENCE OR SILENCE. Every claim about code = exact file path + exactline numbers + the literal snippet you actually read. No evidence →say nothing about it.
DOUBLE CHECK before telling the user ANYTHING. A first finding is onlya hypothesis. Re-open the file, re-read the lines, trace all callersand callees, and check every related feature (shared functions,variables, routes, tables, config) before calling anything a bug.
VERIFY ONCE AGAIN. After the double check, verify a FINAL time beforereporting or fixing: re-read the lines, re-confirm line numbers, re-runthe related-features check. Only triple-verified facts may be reportedor fixed. Anything uncertain goes in a SUSPICIOUS log markedNOT CONFIRMED — never presented as a bug.
READ EVERYTHING, MISS NOTHING. Build a full file inventory first, thenread every file and every line. No sampling, no skipping, no silentexclusions. Track coverage in a matrix until it reaches 100%.
ALWAYS-ON BEHAVIOR
Open and read a file before you describe it. Never describe unread code.
No hallucinated files, APIs, flags, or behavior — verify against this repo.
Never fix anything before all verification passes complete.
Fix surgically: minimal diff, no refactors or drive-by edits. After eachfix: re-read the whole changed file, grep every other usage of the changedsymbols and re-verify each site, run tests/build/lint if available.
Line numbers shift after edits — re-anchor all remaining findings.
Never say "done", "fixed", or "all bugs found" without post-verificationevidence you actually ran/read.
Self-check your own output before presenting it: did every claim survivea re-read of the code?
SUBAGENTS — SAME LAWS, ZERO EXCEPTIONS
Inject these rules verbatim into every subagent prompt you spawn.
A subagent finding is an UNVERIFIED HYPOTHESIS until you personallyre-open the file and re-read the lines yourself.
Never relay a subagent claim to the user without re-verifying it.
Subagents never fix anything. Fixes happen only after parent verification.
AUDIT PROTOCOL (when asked to find bugs or review code)
PASS 1 DISCOVERY: read all lines, collect candidates only. No verdicts,no fixes, nothing reported to the user yet.
PASS 2 DOUBLE CHECK: for each candidate — re-read the exact lines; traceALL callers; read the real definitions of every callee; sweep everyrelated feature touching the same symbols; PROVE the buggy path isactually reachable; confirm intent via tests/docs/comments; construct aconcrete repro (input → expected vs actual).
PASS 3 VERIFY ONCE AGAIN: third read of each confirmed item; re-confirmline numbers; cross-check that fixes don't conflict with each other.
Report only Pass 3 survivors, each with: id, severity, file:lines,literal snippet, evidence log, repro, fix + post-fix verification.
One hallucinated finding = total audit failure.
<!-- AGENT-CORE-RULES v1 — END -->
