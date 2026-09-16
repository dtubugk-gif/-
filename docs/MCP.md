# MCP servers for this repository

`.mcp.json` at the repository root declares the servers below for every Claude Code session opened in this
project; `.claude/settings.json` approves them so nobody has to accept each one by hand. Servers marked
*OAuth* need a one-time sign-in: run `/mcp` inside Claude Code, pick the server, and follow the browser
prompt. Servers started with `npx` need Node 20 or newer on the machine.

| Server | Transport | Sign-in | What it gives the agent | Useful here? |
|---|---|---|---|---|
| `github` | remote, `https://api.githubcopilot.com/mcp/` | OAuth (or a PAT header) | pull requests, issues, checks, code search on GitHub | yes, the repo lives there |
| `context7` | remote, `https://mcp.context7.com/mcp` | none (API key optional) | current documentation for libraries (Compose, Hilt, Room, Lottie…) | yes |
| `figma` | remote, `https://mcp.figma.com/mcp` | OAuth | read designs, export screens, push code into Figma | yes, for the design canvas |
| `supabase` | remote, `https://mcp.supabase.com/mcp` | OAuth | Postgres, auth, edge functions of a Supabase project | only if a backend is added; the app is offline by design |
| `expo` | remote, `https://mcp.expo.dev/mcp` | OAuth (Expo account) | Expo / EAS projects | no, this is a native Kotlin app, not React Native |
| `paper` | local, `http://127.0.0.1:29979/mcp` | the Paper desktop app must be open with a file | read and write Paper design canvases | only if designs move to Paper |
| `chrome-devtools` | `npx chrome-devtools-mcp@latest` | none; needs Chrome on the machine | drive a real Chrome: navigate, click, screenshots, network, performance | only for web pages (Play listing, docs) |
| `playwright` | `npx @playwright/mcp@latest` | none | headless browser automation via accessibility snapshots | same as above |
| `sequential-thinking` | `npx @modelcontextprotocol/server-sequential-thinking` | none | a scratchpad tool for step-by-step reasoning | marginal, the model already reasons |
| `filesystem` | `npx @modelcontextprotocol/server-filesystem .` | none | read / write files under the repository | redundant with Claude Code's own file tools |

## Honest notes

* The repository is a native Android app (Kotlin, Compose, Gradle). Of the ten servers, three earn their
  place daily: GitHub, Context7 and Figma. The rest are wired because they were asked for; they cost nothing
  while idle, but each remote server that is signed in widens what an agent can touch, so sign in only to
  the ones you use.
* Remote servers are approved per machine the first time Claude Code sees `.mcp.json`; `/mcp` shows their
  state. `claude mcp reset-project-choices` clears the approvals.
* `filesystem` is scoped to the repository root (`.`). Do not widen it to `/` or a home directory.
* Chrome DevTools attaches to a Chrome started with `--remote-debugging-port=9222` when you want it to drive
  an existing profile; otherwise it launches its own.
