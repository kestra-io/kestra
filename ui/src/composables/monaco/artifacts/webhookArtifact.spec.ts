import {describe, expect, it} from "vitest"
import {webhookArtifactProvider} from "./webhookArtifact"
import {ARTIFACT_COPY_COMMAND, type ArtifactBlock, type EditorArtifactContext} from "./editorArtifacts"
import {WEBHOOK_TRIGGER_TYPE} from "../../../utils/webhook"

const mockContext: EditorArtifactContext = {
    namespace: "company.team",
    id: "my-flow",
    t: (key: string) => key,
}

const validWebhookBlock: ArtifactBlock = {
    type: WEBHOOK_TRIGGER_TYPE,
    value: {id: "my-webhook", type: WEBHOOK_TRIGGER_TYPE, key: "secret-token-123"},
    range: [10, 50, 50],
    path: "triggers",
}

describe("webhookArtifactProvider", () => {
    it("matches the webhook trigger type and ignores non-trigger blocks", () => {
        expect(webhookArtifactProvider.type).toBe(WEBHOOK_TRIGGER_TYPE)

        const taskBlock: ArtifactBlock = {
            ...validWebhookBlock,
            path: "tasks",
        }
        expect(webhookArtifactProvider.provide(taskBlock, mockContext)).toEqual([])
    })

    it("produces the expected artifact shape for a matched trigger", () => {
        const artifacts = webhookArtifactProvider.provide(validWebhookBlock, mockContext)

        expect(artifacts).toHaveLength(1)
        expect(artifacts[0].title).toBe("webhook.copy_url")
        expect(artifacts[0].command.id).toBe(ARTIFACT_COPY_COMMAND)

        const [payload] = artifacts[0].command.arguments as [{text: string; message: string}]
        expect(payload.text).toContain("/executions/webhook/company.team/my-flow/secret-token-123")
        expect(payload.message).toBe("webhook link copied")
    })

    it("handles triggers missing a key or with invalid key types without throwing", () => {
        const missingKeyBlock: ArtifactBlock = {
            type: WEBHOOK_TRIGGER_TYPE,
            value: {id: "my-webhook", type: WEBHOOK_TRIGGER_TYPE},
            range: [10, 50, 50],
            path: "triggers",
        }
        expect(() => webhookArtifactProvider.provide(missingKeyBlock, mockContext)).not.toThrow()
        expect(webhookArtifactProvider.provide(missingKeyBlock, mockContext)).toEqual([])

        const undefinedKeyBlock: ArtifactBlock = {
            ...validWebhookBlock,
            value: {key: undefined},
        }
        expect(() => webhookArtifactProvider.provide(undefinedKeyBlock, mockContext)).not.toThrow()
        expect(webhookArtifactProvider.provide(undefinedKeyBlock, mockContext)).toEqual([])

        const objectKeyBlock: ArtifactBlock = {
            ...validWebhookBlock,
            value: {key: {nested: "invalid"}},
        }
        expect(() => webhookArtifactProvider.provide(objectKeyBlock, mockContext)).not.toThrow()
        expect(webhookArtifactProvider.provide(objectKeyBlock, mockContext)).toEqual([])
    })

    it("yields nothing when the source context has missing namespace or flow id", () => {
        expect(
            webhookArtifactProvider.provide(validWebhookBlock, {...mockContext, namespace: undefined}),
        ).toEqual([])

        expect(
            webhookArtifactProvider.provide(validWebhookBlock, {...mockContext, id: undefined}),
        ).toEqual([])
    })

    it("yields nothing when block path is not triggers", () => {
        const pluginDefaultBlock: ArtifactBlock = {
            ...validWebhookBlock,
            path: "pluginDefaults",
        }
        expect(webhookArtifactProvider.provide(pluginDefaultBlock, mockContext)).toEqual([])
    })
})
