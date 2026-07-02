#!/usr/bin/env node
// Merge istanbul `coverage-final.json` files produced by sharded test runs into
// a single combined coverage report.
//
// Why this exists: the storybook component tests run in a real browser, and v8
// precise coverage accumulates per-script data in the chromium renderer process
// for the whole run. Across the full story set that grows until the renderer is
// OOM-killed ("page closed unexpectedly"). We therefore shard the run into
// several smaller processes (`--shard=i/N`), each emitting its own
// `coverage-final.json`. Vitest's own `--merge-reports --coverage` would combine
// them, but its v8 provider remaps coverage through a native parser
// (`parseAstAsync`) that heap-corrupts when merging the union of all shards
// (SIGSEGV / SIGABRT on teardown). Merging the already-remapped istanbul JSON in
// pure JS avoids that native path entirely.
//
// Usage: node scripts/merge-coverage.mjs <input-dir> [output-dir]
//   <input-dir>  directory searched recursively for `coverage-final.json`
//   [output-dir] where to write the merged report (default: <input-dir>/merged)
import {readFileSync, readdirSync, statSync, existsSync, mkdirSync} from "node:fs"
import {join, resolve} from "node:path"
import libCoverage from "istanbul-lib-coverage"
import {createContext} from "istanbul-lib-report"
import reports from "istanbul-reports"

const inputDir = resolve(process.argv[2] ?? "coverage")
const outputDir = resolve(process.argv[3] ?? join(inputDir, "merged"))

function findCoverageFiles(dir) {
    const found = []
    for (const entry of readdirSync(dir)) {
        const fullPath = join(dir, entry)
        if (statSync(fullPath).isDirectory()) {
            if (fullPath === outputDir) continue // never re-ingest our own output
            found.push(...findCoverageFiles(fullPath))
        } else if (entry === "coverage-final.json") {
            found.push(fullPath)
        }
    }
    return found
}

if (!existsSync(inputDir)) {
    console.error(`merge-coverage: input directory not found: ${inputDir}`)
    process.exit(1)
}

const files = findCoverageFiles(inputDir)
if (files.length === 0) {
    console.error(`merge-coverage: no coverage-final.json found under ${inputDir}`)
    process.exit(1)
}

const coverageMap = libCoverage.createCoverageMap({})
for (const file of files) {
    coverageMap.merge(JSON.parse(readFileSync(file, "utf8")))
}

mkdirSync(outputDir, {recursive: true})
const context = createContext({dir: outputDir, coverageMap})
reports.create("json", {file: "coverage-final.json"}).execute(context)
reports.create("lcovonly", {file: "lcov.info"}).execute(context)
reports.create("html").execute(context)
reports.create("text-summary").execute(context)

console.log(`\nmerge-coverage: combined ${files.length} report(s) → ${outputDir}`)
