import fs from "node:fs/promises"
import path from "node:path"
import {fileURLToPath} from "node:url"

const SEED_DIR = path.dirname(fileURLToPath(import.meta.url))
const TERMINAL_STATES = new Set(["SUCCESS", "WARNING", "FAILED", "KILLED", "CANCELLED", "SKIPPED", "PAUSED"])

const run = (namespace, flowId, inputs = {}) => ({namespace, flowId, inputs})
const times = (count, execution) => Array.from({length: count}, () => execution)

const EXECUTIONS = [
    ...[50, 100, 150].map((size) => run("company.data", "process_orders", {batch_size: size, simulate_failure: false})),
    run("company.data", "process_orders", {batch_size: 80, simulate_failure: true}),
    ...["search_ads", "social_ads", "display"].map((platform) => run("company.marketing", "campaign_sync", {platform, api_available: true})),
    run("company.marketing", "campaign_sync", {platform: "social_ads", api_available: false}),
    run("company.marketing", "campaign_sync", {platform: "display", api_available: false}),
    ...times(3, run("company.marketing", "newsletter_dispatch")),
    ...["Alice", "Bob", "Kestra", "Team"].map((name) => run("company.team", "hello_world", {user_name: name})),
    ...["web", "mobile", "partner_api"].map((source) => run("company.data", "ingest_events", {source})),
    ...times(3, run("company.team", "kv_housekeeping")),
    ...times(3, run("company.data", "daily_sales_report")),
    ...times(2, run("company.team", "approval_workflow")),
]

/**
 * Creates the flows the journeys rely on and runs a mix of SUCCESS, FAILED, WARNING and PAUSED executions.
 * Safe to rerun: existing flows are left untouched, and executions are only started when there are none yet.
 */
export async function seed(api, log) {
    const created = []
    for (const file of await yamlFiles("flows")) {
        const yaml = await fs.readFile(path.join(SEED_DIR, "flows", file), "utf8")
        const {namespace, id} = flowKey(yaml)
        if (!await api.get(`/main/flows/${namespace}/${id}`, {optional: true})) {
            await api.post("/main/flows", {body: yaml, contentType: "application/x-yaml"})
            created.push(`${namespace}.${id}`)
        }
    }
    log(`created ${created.length} flow(s)`)

    const {total} = await api.get("/main/executions/search?size=1")
    if (total === 0) {
        const ids = []
        for (const {namespace, flowId, inputs} of EXECUTIONS) {
            const form = new FormData()
            Object.entries(inputs).forEach(([name, value]) => form.append(name, String(value)))
            ids.push((await api.post(`/main/executions/${namespace}/${flowId}`, {body: form})).id)
            await new Promise((resolve) => setTimeout(resolve, 1500))
        }
        await waitForExecutions(api, ids)
        log(`ran ${ids.length} execution(s)`)
    }

    for (const file of await yamlFiles("revisions")) {
        const yaml = await fs.readFile(path.join(SEED_DIR, "revisions", file), "utf8")
        const {namespace, id} = flowKey(yaml)
        if (created.includes(`${namespace}.${id}`)) {
            await api.put(`/main/flows/${namespace}/${id}`, {body: yaml, contentType: "application/x-yaml"})
            log(`published a new revision of ${namespace}.${id}`)
        }
    }
}

async function yamlFiles(folder) {
    return (await fs.readdir(path.join(SEED_DIR, folder))).filter((file) => file.endsWith(".yml")).sort()
}

function flowKey(yaml) {
    return {
        id: yaml.match(/^id:\s*(\S+)/m)[1],
        namespace: yaml.match(/^namespace:\s*(\S+)/m)[1],
    }
}

async function waitForExecutions(api, ids) {
    const deadline = Date.now() + 120_000
    for (const id of ids) {
        while (!TERMINAL_STATES.has((await api.get(`/main/executions/${id}`)).state.current)) {
            if (Date.now() > deadline) {
                throw new Error(`Execution '${id}' did not finish within two minutes of seeding.`)
            }
            await new Promise((resolve) => setTimeout(resolve, 1000))
        }
    }
}
