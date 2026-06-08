import {describe, expect, it} from "vitest"
import {
    provideEditorArtifacts,
    registerEditorArtifactProvider,
    ARTIFACT_COPY_COMMAND,
    type ArtifactBlock,
} from "../../../../../src/composables/monaco/artifacts"

const t = (key: string) => key

const webhookBlock: ArtifactBlock = {
    type: "io.kestra.plugin.core.trigger.Webhook",
    value: {id: "webhook", type: "io.kestra.plugin.core.trigger.Webhook", key: "admin1234"},
    range: [40, 80, 80],
    path: "triggers",
}

describe("provideEditorArtifacts", () => {
    it("resolves a copy lens for a webhook trigger block", () => {
        const artifacts = provideEditorArtifacts([webhookBlock], {namespace: "company.team", id: "my-flow", t})

        expect(artifacts).toHaveLength(1)
        expect(artifacts[0].range).toEqual([40, 80, 80])
        expect(artifacts[0].lens.title).toBe("webhook.copy_url")
        expect(artifacts[0].lens.command.id).toBe(ARTIFACT_COPY_COMMAND)

        const [payload] = artifacts[0].lens.command.arguments as [{text: string; message: string}]
        expect(payload.text).toContain("/executions/webhook/company.team/my-flow/admin1234")
        expect(payload.message).toBe("webhook link copied")
    })

    it("skips the webhook block when the key is missing", () => {
        const artifacts = provideEditorArtifacts(
            [{...webhookBlock, value: {id: "webhook", type: webhookBlock.type}}],
            {namespace: "company.team", id: "my-flow", t},
        )

        expect(artifacts).toEqual([])
    })

    it("skips blocks without a matching provider", () => {
        const logBlock: ArtifactBlock = {
            type: "io.kestra.plugin.core.log.Log",
            value: {id: "hello", type: "io.kestra.plugin.core.log.Log"},
            range: [0, 10, 10],
            path: "tasks",
        }

        expect(provideEditorArtifacts([logBlock], {namespace: "n", id: "f", t})).toEqual([])
    })

    it("skips a webhook-typed block that is not inside the triggers section", () => {
        const pluginDefaultBlock: ArtifactBlock = {
            type: "io.kestra.plugin.core.trigger.Webhook",
            value: {type: "io.kestra.plugin.core.trigger.Webhook", key: "shared"},
            range: [10, 40, 40],
            path: "pluginDefaults",
        }

        expect(provideEditorArtifacts([pluginDefaultBlock], {namespace: "company.team", id: "my-flow", t})).toEqual([])
    })

    it("does not register duplicate providers for the same type", () => {
        const provider = {type: "io.kestra.plugin.core.trigger.Webhook", provide: () => []}
        registerEditorArtifactProvider(provider)

        const artifacts = provideEditorArtifacts([webhookBlock], {namespace: "company.team", id: "my-flow", t})
        expect(artifacts).toHaveLength(1)
    })
})
