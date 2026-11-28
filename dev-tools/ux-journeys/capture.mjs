import fs from "node:fs/promises"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {parseArgs, stripVTControlCharacters} from "node:util"

import {apiClient, launchBrowser, login, newPage, shooter, skipProductTour, waitForInstance} from "./lib.mjs"
import {seed} from "./seed/seed.mjs"

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SETUP_JOURNEY = "first-launch"
const SEED = "seed"
const PLAN = [
    SETUP_JOURNEY,
    "create-flow",
    SEED,
    "run-execution",
    "debug-failed-execution",
    "executions-filters",
    "namespace-files",
    "blueprints",
    "plugins-docs",
    "logs-search",
    "triggers",
    "dark-mode",
    "dashboard-overview",
    "system-alert-wizard",
    "flow-revisions",
    "product-tour",
]
const ALL_JOURNEYS = PLAN.filter((step) => step !== SEED)

const {values: args} = parseArgs({
    options: {
        "url": {type: "string", default: process.env.KESTRA_URL ?? "http://localhost:8080"},
        "out": {type: "string", default: process.env.UX_JOURNEYS_OUT ?? path.join(ROOT, "out")},
        "journeys": {type: "string", default: process.env.UX_JOURNEYS ?? ""},
    },
})
const baseUrl = args.url.replace(/\/$/, "")
const outDir = path.resolve(args.out)
const credentials = {
    username: process.env.KESTRA_USERNAME ?? "admin@kestra.local",
    password: process.env.KESTRA_PASSWORD ?? "Kestra2026Ux",
}
const api = apiClient({baseUrl, ...credentials})
const log = (message) => console.log(`[ux-journeys] ${message}`)

const selected = args.journeys ? args.journeys.split(",").map((id) => id.trim()).filter(Boolean) : ALL_JOURNEYS
const unknown = selected.filter((id) => !ALL_JOURNEYS.includes(id))
if (unknown.length) {
    console.error(`Unknown journey(s): ${unknown.join(", ")}. Known journeys: ${ALL_JOURNEYS.join(", ")}.`)
    process.exit(2)
}

log(`waiting for Kestra on ${baseUrl}`)
const {isBasicAuthInitialized} = await waitForInstance(baseUrl, 300_000)
const toRun = new Set(selected)
if (!isBasicAuthInitialized) {
    toRun.add(SETUP_JOURNEY)
}

await fs.mkdir(outDir, {recursive: true})
const failures = []
const captured = []
const browser = await launchBrowser()
let storageState

try {
    for (const step of PLAN) {
        if (step === SEED) {
            if (PLAN.slice(PLAN.indexOf(SEED)).some((id) => toRun.has(id))) {
                log("seeding flows and executions")
                await seed(api, (message) => log(`  ${message}`))
            }
            continue
        }
        if (!toRun.has(step)) {
            continue
        }
        if (step === SETUP_JOURNEY && isBasicAuthInitialized) {
            failures.push({id: step, error: "The setup wizard only shows on a fresh instance, and this one already has an admin user."})
            continue
        }
        if (step !== SETUP_JOURNEY) {
            storageState ??= await authenticate()
        }
        if (await runJourney(step)) {
            captured.push(step)
        } else if (step === SETUP_JOURNEY) {
            throw new Error("The setup wizard failed, so no other journey can log in.")
        }
    }
} catch (error) {
    failures.push({id: "run", error: error.message})
} finally {
    await browser.close()
}

const journeys = await Promise.all(captured.map(loadJourney))
const version = await instanceVersion().catch((error) => {
    failures.push({id: "version", error: error.message})
    return "unknown"
})
const manifest = journeys.map((journey) => ({
    id: journey.id,
    title: journey.title,
    summary: journey.summary,
    source: "kestra",
    product: "Kestra",
    version,
    category: journey.category,
    area: journey.area,
    theme: journey.theme ?? "light",
    tags: journey.tags,
    steps: journey.steps,
    edges: journey.edges,
}))
const problems = await validate(manifest)
await fs.writeFile(path.join(outDir, "journeys.json"), `${JSON.stringify(manifest, null, 2)}\n`)

log(`${captured.length} journey(s) captured into ${outDir}`)
for (const {id, error} of failures) {
    log(`FAILED ${id}: ${error}`)
}
for (const problem of problems) {
    log(`INVALID ${problem}`)
}
process.exitCode = failures.length || problems.length ? 1 : 0

async function loadJourney(id) {
    return (await import(`./journeys/${id}.mjs`)).journey
}

async function authenticate() {
    const page = await newPage(browser, {baseUrl})
    await login(page, credentials)
    await skipProductTour(page)
    const state = await page.context().storageState()
    await page.context().close()
    return state
}

async function runJourney(id) {
    const {journey, capture} = await import(`./journeys/${id}.mjs`)
    const page = await newPage(browser, {baseUrl, storageState})
    const startedAt = Date.now()
    log(`${id}: capturing ${journey.steps.length} step(s)`)
    try {
        const shot = await shooter(page, journey, outDir)
        await capture({page, shot, api, credentials})
        log(`${id}: done in ${Math.round((Date.now() - startedAt) / 1000)}s`)
        return true
    } catch (error) {
        failures.push({id, error: summarize(error)})
        await fs.mkdir(path.join(outDir, "errors"), {recursive: true})
        await page.screenshot({path: path.join(outDir, "errors", `${id}.jpg`), type: "jpeg", quality: 60}).catch(() => {})
        return false
    } finally {
        await page.context().close()
    }
}

function summarize(error) {
    const lines = stripVTControlCharacters(error.message).split("\n")
    const target = lines.find((line) => line.includes("waiting for "))
    return target ? `${lines[0]} ${target.trim()}` : lines[0]
}

async function instanceVersion() {
    const {version} = await api.get("/configs")
    return version.endsWith("-SNAPSHOT") ? `develop (${version})` : version
}

async function validate(entries) {
    const problems = []
    for (const {id, steps, edges} of entries) {
        const keys = steps.map((step) => step.key)
        if (new Set(keys).size !== keys.length) {
            problems.push(`${id}: step keys are not unique`)
        }
        const files = new Set(await fs.readdir(path.join(outDir, "shots", id)).catch(() => []))
        for (const step of steps) {
            if (!files.has(step.file)) {
                problems.push(`${id}/${step.key}: ${step.file} was not captured`)
            }
            files.delete(step.file)
        }
        if (files.size) {
            problems.push(`${id}: unexpected file(s) ${[...files].join(", ")}`)
        }
        for (const edge of edges) {
            if (!keys.includes(edge.from) || !keys.includes(edge.to)) {
                problems.push(`${id}: edge ${edge.from} -> ${edge.to} references an unknown step`)
            }
        }
    }
    return problems
}
