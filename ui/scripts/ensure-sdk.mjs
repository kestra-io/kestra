#!/usr/bin/env node
// predev / prebuild / precheck:types hook. See sdk-lib.mjs for what this does and why.
import path from "node:path"
import {fileURLToPath} from "node:url"
import {ensureSdkBundled} from "./sdk-lib.mjs"

const uiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
ensureSdkBundled(uiRoot)
