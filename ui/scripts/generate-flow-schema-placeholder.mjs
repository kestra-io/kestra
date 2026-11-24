#!/usr/bin/env node
// @ts-check

// Rebuilds src/stores/flow-schema.json, the schema the no-code editor renders with until the full one is loaded.
// Usage: node scripts/generate-flow-schema-placeholder.mjs <file or URL of GET /api/v1/plugins/schemas/flow>
import {readFileSync, writeFileSync} from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {inflateCompactSchema} from "../src/utils/compactSchema.ts"

const OUTPUT = path.join(path.dirname(fileURLToPath(import.meta.url)), "../src/stores/flow-schema.json")
const DEFINITIONS = "#/definitions/"
// The one plugin kept, so that a list of plugins still reads as a list of typed blocks.
const SAMPLE_PLUGIN = "io.kestra.plugin.core.log.Log"
// Root property holding each list of plugins, and the definition standing in for that list.
const PLUGIN_LISTS = {tasks: "io.kestra.preload.tasks", triggers: "io.kestra.preload.triggers"}

const source = process.argv[2]
if (!source) {
    console.error("Usage: node scripts/generate-flow-schema-placeholder.mjs <file or URL of the flow schema>")
    process.exit(1)
}

// The endpoint serves the compact schema, whose subtype lists sit behind shared-* refs this script compares inline.
const full = inflateCompactSchema(JSON.parse(/^https?:/.test(source) ? await (await fetch(source)).text() : readFileSync(source, "utf8")))
const rootKey = full.$ref.slice(DEFINITIONS.length)
const root = structuredClone(full.definitions[rootKey])

/** @param {{items?: {anyOf?: unknown[]}}} property */
const subtypesOf = (property) => JSON.stringify(property.items?.anyOf)
const standInBySubtypes = new Map(Object.entries(PLUGIN_LISTS).map(([property, standIn]) => [subtypesOf(root.properties[property]), standIn]))

// `errors`, `finally` and `afterExecution` repeat the list of tasks, so they share its stand-in.
for (const [name, property] of Object.entries(root.properties)) {
    const standIn = standInBySubtypes.get(subtypesOf(property))
    if (standIn) {
        root.properties[name] = {$ref: DEFINITIONS + standIn}
    }
}

/** @type {Record<string, unknown>} */
const definitions = {[rootKey]: root, [SAMPLE_PLUGIN]: full.definitions[SAMPLE_PLUGIN]}
for (const standIn of Object.values(PLUGIN_LISTS)) {
    definitions[standIn] = {type: "array", items: {anyOf: [{$ref: DEFINITIONS + SAMPLE_PLUGIN}]}}
}

/** @param {unknown} node @param {(key: string) => void} visit */
function forEachRef(node, visit) {
    if (Array.isArray(node)) {
        node.forEach((child) => forEachRef(child, visit))
    } else if (node && typeof node === "object") {
        for (const [key, value] of Object.entries(node)) {
            if (key === "$ref" && typeof value === "string") {
                visit(value.slice(DEFINITIONS.length))
            } else {
                forEachRef(value, visit)
            }
        }
    }
}

// Everything the kept definitions still reference comes along, so the placeholder has no dangling $ref.
const pending = Object.values(definitions)
while (pending.length) {
    forEachRef(pending.pop(), (key) => {
        if (!(key in definitions)) {
            definitions[key] = full.definitions[key]
            pending.push(definitions[key])
        }
    })
}

const sorted = Object.fromEntries(Object.keys(definitions).sort().map((key) => [key, definitions[key]]))
writeFileSync(OUTPUT, JSON.stringify({$schema: full.$schema, $ref: full.$ref, definitions: sorted}, null, 2) + "\n")
console.log(`${path.relative(process.cwd(), OUTPUT)}: ${Object.keys(sorted).length} definitions`)
