import {spawnSync} from "node:child_process"
import {createRequire} from "node:module"
import path from "node:path"

// Runs every step even after a failure, so CI reports each project's errors and time.
const tsc = path.join(path.dirname(createRequire(`${process.cwd()}/`).resolve("typescript")), "../bin/tsc")
let failed = false

for (const step of process.argv.slice(2)) {
    const start = performance.now()
    const {status} = spawnSync(process.execPath, [tsc, ...step.split(" "), "--runExternalCode"], {stdio: "inherit"})
    failed ||= status !== 0
    console.log(`${status === 0 ? "passed" : "failed"}: tsc ${step} (${((performance.now() - start) / 1000).toFixed(1)}s)`)
}

process.exitCode = failed ? 1 : 0
