#!/usr/bin/env node
// generate:sdk — the only path that invokes Gradle. See sdk-lib.mjs for what this does and why.
import path from "node:path"
import {fileURLToPath} from "node:url"
import {generateSdk} from "./sdk-lib.mjs"

const uiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
generateSdk(uiRoot)
