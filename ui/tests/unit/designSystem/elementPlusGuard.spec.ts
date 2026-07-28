import {describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {findElementPlusUsage} from "./elementPlusGuard"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")

describe("design system", () => {
    it("uses Ks* components instead of Element Plus across ui/src", () => {
        const offenders = findElementPlusUsage(SRC)
        expect(
            offenders,
            `Replace Element Plus with Ks* components / --ks-* tokens (see ui/AGENTS.md):\n${offenders.join("\n")}`,
        ).toEqual([])
    })
})
