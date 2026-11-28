# UX journeys

Replays 15 user journeys through the Kestra UI and captures one screenshot per step, plus a
`journeys.json` manifest describing each step (title, caption, screen, UI elements, observation)
and the transitions between them. Rerun it on each new `develop` build to refresh the captures.

## Prerequisites

- Node 22 or later
- A **fresh** Kestra instance: the first journey is the one-time setup wizard that creates the admin
  user, and the following one starts from an empty flow list. Data must not survive a restart, e.g.:

```bash
docker run -d --rm --name kestra-ux -p 8080:8080 kestra/kestra:develop-no-plugins server local
```

## Run

```bash
npm ci
npx playwright install chromium
npm run capture
```

The tool waits for the instance, runs the setup wizard if no admin exists yet, seeds the flows and
executions the journeys rely on (`seed/`), then runs every journey. A failing journey does not stop
the others; failures are listed at the end, with a screenshot in `<out>/errors/`, and the exit code
is non-zero.

## Options

| Option | Environment variable | Default |
|---|---|---|
| `--url` | `KESTRA_URL` | `http://localhost:8080` |
| `--out` | `UX_JOURNEYS_OUT` | `./out` (gitignored) |
| `--journeys` | `UX_JOURNEYS` | all, as a comma list such as `triggers,logs-search` |
| | `KESTRA_USERNAME` | `admin@kestra.local` |
| | `KESTRA_PASSWORD` | `Kestra2026Ux` |

```bash
npm run capture -- --out /tmp/ux --journeys triggers,logs-search
```

Running a subset against an instance that is already set up works for most journeys, but
`first-launch` and `create-flow` only make sense on a fresh one.

## Output

```
<out>/
├── journeys.json          # array of journeys, in run order
├── shots/<journey>/NN.jpg # 1440x900 JPEG, one per step
└── errors/<journey>.jpg   # last screen of a failed journey
```

Each journey in `journeys.json` has `id`, `title`, `summary`, `source`, `product`, `version` (read
from the instance, `develop (x.y.z-SNAPSHOT)` for a snapshot), `category`, `area`, `theme`, `tags`,
`steps` (`key`, `file`, `title`, `caption`, `screen`, `url`, `elements`, `observation`) and `edges`
(`from`, `to`, `label`). The run checks that every step has its file and that every edge links two
existing steps.

## Adding or changing a journey

A journey is one module in `journeys/`: its metadata in `journey`, and a `capture({page, shot, api})`
function that drives the page and calls `shot("<step key>")` for each step. Add its id to `PLAN` in
`capture.mjs`, after `seed` if it needs the seeded data.
