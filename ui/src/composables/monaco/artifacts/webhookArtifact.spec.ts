import {describe, expect, it} from "vitest"
import {webhookArtifactProvider} from "./webhookArtifact"
import {ARTIFACT_COPY_COMMAND, type ArtifactBlock, type EditorArtifactContext} from "./editorArtifacts"
import {WEBHOOK_TRIGGER_TYPE} from "../../../utils/webhook"

const t = (key: string) => key
const context: EditorArtifactContext = {namespace: "company.team", id: "my-flow", t}

const webhookBlock: ArtifactBlock = {
    type: WEBHOOK_TRIGGER_TYPE,
    value: {id: "webhook", type: WEBHOOK_TRIGGER_TYPE, key: "admin1234"},
    range: [40, 80, 80],
    path: "triggers",
}

describe("webhookArtifactProvider", () => {
    it("matches a webhook trigger and ignores other trigger types", () => {
        expect(webhookArtifactProvider.type).toBe(WEBHOOK_TRIGGER_TYPE)
        expect(webhookArtifactProvider.type).not.toBe("io.kestra.plugin.core.trigger.Schedule")
    })

    it("produces the expected artifact shape for a matched trigger", () => {
        const artifacts = webhookArtifactProvider.provide(webhookBlock, context)

        expect(artifacts).toHaveLength(1)
        expect(artifacts[0].title).toBe("webhook.copy_url")
        expect(artifacts[0].command.id).toBe(ARTIFACT_COPY_COMMAND)

        const [payload] = artifacts[0].command.arguments as [{text: string; message: string}]
        expect(payload.text).toContain("/executions/webhook/company.team/my-flow/admin1234")
        expect(payload.message).toBe("webhook link copied")
    })

    it("handles a trigger missing its key rather than throwing", () => {
        const blockWithoutKey: ArtifactBlock = {...webhookBlock, value: {id: "webhook", type: WEBHOOK_TRIGGER_TYPE}}

        expect(() => webhookArtifactProvider.provide(blockWithoutKey, context)).not.toThrow()
        expect(webhookArtifactProvider.provide(blockWithoutKey, context)).toEqual([])
    })

    it("yields nothing for a source with no triggers", () => {
        const taskBlock: ArtifactBlock = {...webhookBlock, path: "tasks"}

        expect(webhookArtifactProvider.provide(taskBlock, context)).toEqual([])
    })
})
