import {describe, expect, it} from "vitest"
import {isScannedSourceFile, translationKeyUsages, undefinedKeyUsages} from "./usageRules.mjs"

const keysOf = (source) => translationKeyUsages(source).map(({key}) => key)

describe("translationKeyUsages", () => {
    it("collects every call shape the app uses", () => {
        const source = [
            "t(\"flows.create\")",
            "$t('yes')",
            "t(`demos.tests.title`)",
            "i18n.global.t(\"saved\")",
            "ctx.t(\"block_editor.command_menu.context_flow\", {name})",
            "const label = t(\"plugin_card.tasks\", count)",
            "$tm(\"change state hint\")",
            "<i18n-t keypath=\"ai.copilot.title\" tag=\"span\">",
        ].join("\n")

        expect(keysOf(source)).toEqual([
            "flows.create",
            "yes",
            "demos.tests.title",
            "saved",
            "block_editor.command_menu.context_flow",
            "plugin_card.tasks",
            "change state hint",
            "ai.copilot.title",
        ])
    })

    it("keeps keys that contain spaces, dashes and parentheses", () => {
        expect(keysOf("t(\"flows not imported\"); $t(\"cluster-worker-group\"); t(\"execution(s) killed\")"))
            .toEqual(["flows not imported", "cluster-worker-group", "execution(s) killed"])
    })

    it("skips keys built at runtime", () => {
        const source = [
            "t(\"errors.\" + code + \".title\")",
            "t(`ai.copilot.error.${error}`)",
            "$t(e.message)",
            "<i18n-t :keypath=\"pill.keypath\">",
            "t(\"crud.type.\")",
        ].join("\n")

        expect(keysOf(source)).toEqual([])
    })

    it("does not mistake other functions for the translator", () => {
        expect(keysOf("format(\"a.b\"); at(\"x\"); foo.at(\"y\"); test(\"z\"); const t = \"q\"")).toEqual([])
    })

    it("marks existence tests as guards", () => {
        expect(translationKeyUsages("te(\"maybe.absent\"); $te(\"also.absent\"); t(\"present\")")).toEqual([
            {key: "maybe.absent", line: 1, guarded: true},
            {key: "also.absent", line: 1, guarded: true},
            {key: "present", line: 1, guarded: false},
        ])
    })

    it("reports the 1-based line of each usage", () => {
        const source = "<template>\n  <span>{{ $t(\"first\") }}</span>\n</template>\n<script setup>\nconst x = t(\"second\")\n</script>\n"
        expect(translationKeyUsages(source).map(({key, line}) => [key, line])).toEqual([["first", 2], ["second", 5]])
    })
})

describe("undefinedKeyUsages", () => {
    const usages = {
        "a.vue": [{key: "defined.leaf", line: 1, guarded: false}, {key: "missing", line: 2, guarded: false}],
        "b.ts": [{key: "defined", line: 4, guarded: false}, {key: "checked.first", line: 5, guarded: true}],
        "c.ts": [{key: "checked.first", line: 7, guarded: false}],
    }
    const defined = new Set(["defined", "defined.leaf"])

    it("reports every usage of a key that no key set defines", () => {
        expect(undefinedKeyUsages(usages, defined)).toEqual([{file: "a.vue", line: 2, key: "missing"}])
    })

    it("accepts a namespace as defined, since the app reads object nodes on purpose", () => {
        expect(undefinedKeyUsages({"a.vue": [{key: "defined", line: 1, guarded: false}]}, defined)).toEqual([])
    })

    it("exempts a key anywhere once one file tests its existence", () => {
        expect(undefinedKeyUsages(usages, defined).map(({key}) => key)).not.toContain("checked.first")
    })
})

describe("isScannedSourceFile", () => {
    it("scans application sources", () => {
        for (const file of ["ui/src/App.vue", "ui/src/stores/flow.ts", "ui/src/utils/toast.js", "ui/packages/topology/src/Graph.vue"]) {
            expect(isScannedSourceFile(file), file).toBe(true)
        }
    })

    it("skips tests, stories, declarations, locale files and generated or vendored trees", () => {
        for (const file of [
            "ui/src/App.spec.ts",
            "ui/src/App.test.js",
            "ui/src/App.stories.ts",
            "ui/src/types.d.ts",
            "ui/packages/design-system/src/components/KsEmpty.locale.ts",
            "ui/src/translations/i18n.ts",
            "ui/node_modules/vue/index.js",
            "ui/dist/app.js",
            "ui/src/__tests__/helper.ts",
            "ui/src/styles/app.scss",
        ]) {
            expect(isScannedSourceFile(file), file).toBe(false)
        }
    })
})
