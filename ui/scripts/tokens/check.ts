import path from "path"
import {fileURLToPath} from "url"
import {checkTokens, reportTokens} from "./checkTokens.ts"

const here = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(here, "../..")

process.exit(reportTokens(
    checkTokens(
        [path.join(uiRoot, "src"), path.join(uiRoot, "packages")],
        path.join(uiRoot, "packages/design-system/tests/storybook/Basic/Color-variables.json"),
    ),
    uiRoot,
))
