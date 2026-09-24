import {describe, it, expect} from "vitest"
import {buildPickerEntries} from "../../../../../src/components/no-code/blocks/taskPickerCatalog"

describe("buildPickerEntries", () => {
    // GET /plugins/groups/subgroups returns every plugin twice: once with subGroup null,
    // carrying every class in the jar, then once per subgroup, carrying only that
    // subgroup's classes. Real fixtures reproduce that two-tier shape rather than a
    // single flat entry per plugin, since `subGroup` on the entry cannot be trusted.
    const pluginLevelEntry = (group: string, name: string, classes: string[]) => ({
        title: group,
        name,
        subGroup: null,
        tasks: classes.map(cls => ({cls})),
    })

    const subgroupEntry = (group: string, name: string, subGroup: string, classes: string[]) => ({
        title: group,
        name,
        subGroup,
        tasks: classes.map(cls => ({cls})),
    })

    it("qualifies the label with the class's own package when the short name collides across plugins", () => {
        const entries = buildPickerEntries([
            pluginLevelEntry("AWS", "aws", ["io.kestra.plugin.aws.s3.Trigger", "io.kestra.plugin.aws.sqs.Trigger"]),
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.s3", ["io.kestra.plugin.aws.s3.Trigger"]),
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.sqs", ["io.kestra.plugin.aws.sqs.Trigger"]),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["Trigger (s3)", "Trigger (sqs)"])
    })

    it("leaves an unambiguous label untouched", () => {
        const entries = buildPickerEntries([
            pluginLevelEntry("AWS", "aws", ["io.kestra.plugin.aws.s3.Upload", "io.kestra.plugin.aws.sqs.Trigger"]),
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.s3", ["io.kestra.plugin.aws.s3.Upload"]),
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.sqs", ["io.kestra.plugin.aws.sqs.Trigger"]),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["Upload", "Trigger"])
    })

    it("cannot disambiguate two classes that share a title in the same package", () => {
        const entries = buildPickerEntries([
            {
                title: "Core",
                name: "core",
                subGroup: null,
                tasks: [
                    {cls: "io.kestra.plugin.core.trigger.Foo", title: "Trigger"},
                    {cls: "io.kestra.plugin.core.trigger.Bar", title: "Trigger"},
                ],
            },
        ], "tasks")

        // Both classes qualify to the same "core.trigger" fallback and still collide - a real
        // limit, not a bug: two classes sharing a title in the same subGroup have nothing left
        // to disambiguate with.
        expect(entries.map(entry => entry.label)).toEqual(["Trigger (core.trigger)", "Trigger (core.trigger)"])
    })

    it("falls back to the last two subgroup segments when the short qualifier itself collides", () => {
        const entries = buildPickerEntries([
            pluginLevelEntry("AWS", "aws", ["io.kestra.plugin.aws.storage.Trigger"]),
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.storage", ["io.kestra.plugin.aws.storage.Trigger"]),
            pluginLevelEntry("GCP", "gcp", ["io.kestra.plugin.gcp.storage.Trigger"]),
            subgroupEntry("GCP", "gcp", "io.kestra.plugin.gcp.storage", ["io.kestra.plugin.gcp.storage.Trigger"]),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual([
            "Trigger (aws.storage)",
            "Trigger (gcp.storage)",
        ])
    })

    it("prefers an explicit title over the qualified fallback", () => {
        const entries = buildPickerEntries([
            {
                title: "AWS",
                name: "aws",
                subGroup: null,
                tasks: [
                    {cls: "io.kestra.plugin.aws.s3.Trigger", title: "S3 Trigger"},
                    {cls: "io.kestra.plugin.aws.sqs.Trigger"},
                ],
            },
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.s3", ["io.kestra.plugin.aws.s3.Trigger"]),
            subgroupEntry("AWS", "aws", "io.kestra.plugin.aws.sqs", ["io.kestra.plugin.aws.sqs.Trigger"]),
        ], "tasks")

        expect(entries.map(entry => entry.label)).toEqual(["S3 Trigger", "Trigger"])
    })
})
