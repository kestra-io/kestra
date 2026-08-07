import {describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"

import en from "../../../src/translations/en.json"
import {findUnresolvedTableColumnKeys, mergeMessages} from "./tableColumnGuard"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")

// The column-picker descriptions live in the design-system locale files, which
// `registerDesignSystemI18n` merges into the app messages at bootstrap.
const designSystemLocales = import.meta.glob<{default: Record<string, any>}>(
    "../../../packages/design-system/src/components/**/*.locale.ts",
    {eager: true},
)

describe("table column descriptions", () => {
    it("resolves every filter.table_column.* key referenced in ui/src", () => {
        const messages = Object.values(designSystemLocales).reduce(
            (merged, module) => mergeMessages(merged, module.default.en ?? {}),
            {...en.en} as Record<string, any>,
        )

        const unresolved = findUnresolvedTableColumnKeys(SRC, messages)

        expect(
            unresolved,
            `Add the missing English descriptions to the design-system locale file, then run \`npm run translations:generate\`:\n${unresolved.join("\n")}`,
        ).toEqual([])
    })
})
