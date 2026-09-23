import {describe, it, expect} from "vitest"
import {buildPickerEntries} from "../../../../../src/components/no-code/blocks/taskPickerCatalog"

describe("buildPickerEntries", () => {
    const plugin = (overrides: Record<string, unknown>) => ({
        title: "AWS",
        name: "aws",
        ...overrides,
    })

    it("qualifies the label with the subgroup when the short name collides across plugins", () => {
        const entries = buildPickerEntries([
            plugin({subGroup: "io.kestra.plugin.aws.s3", tasks: [{cls: "io.kestra.plugin.aws.s3.Trigger"}]}),
            plugin({subGroup: "io.kestra.plugin.aws.sqs", tasks: [{cls: "io.kestra.plugin.aws.sqs.Trigger"}]}),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["Trigger (s3)", "Trigger (sqs)"])
    })

    it("leaves an unambiguous label untouched", () => {
        const entries = buildPickerEntries([
            plugin({subGroup: "io.kestra.plugin.aws.s3", tasks: [{cls: "io.kestra.plugin.aws.s3.Upload"}]}),
            plugin({subGroup: "io.kestra.plugin.aws.sqs", tasks: [{cls: "io.kestra.plugin.aws.sqs.Trigger"}]}),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["Upload", "Trigger"])
    })

    it("leaves a colliding label untouched when no subgroup can disambiguate it", () => {
        const entries = buildPickerEntries([
            plugin({name: "core", title: "Core", tasks: [{cls: "io.kestra.plugin.core.a.Trigger"}]}),
            plugin({name: "core", title: "Core", tasks: [{cls: "io.kestra.plugin.core.b.Trigger"}]}),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["Trigger", "Trigger"])
    })

    it("falls back to the full subgroup when the short qualifier itself collides", () => {
        const entries = buildPickerEntries([
            plugin({subGroup: "io.kestra.plugin.aws.storage", tasks: [{cls: "io.kestra.plugin.aws.storage.Trigger"}]}),
            plugin({subGroup: "io.kestra.plugin.gcp.storage", name: "gcp", title: "GCP", tasks: [{cls: "io.kestra.plugin.gcp.storage.Trigger"}]}),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual([
            "Trigger (io.kestra.plugin.aws.storage)",
            "Trigger (io.kestra.plugin.gcp.storage)",
        ])
    })

    it("prefers an explicit title over the qualified fallback", () => {
        const entries = buildPickerEntries([
            plugin({subGroup: "io.kestra.plugin.aws.s3", tasks: [{cls: "io.kestra.plugin.aws.s3.Trigger", title: "S3 Trigger"}]}),
            plugin({subGroup: "io.kestra.plugin.aws.sqs", tasks: [{cls: "io.kestra.plugin.aws.sqs.Trigger"}]}),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["S3 Trigger", "Trigger"])
    })
})
