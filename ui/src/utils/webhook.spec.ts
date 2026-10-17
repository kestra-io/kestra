import {describe, expect, it} from "vitest"
import {webhookUrl, WEBHOOK_TRIGGER_TYPE, WEBHOOK_TRIGGER_TYPES} from "./webhook"

describe("webhookUrl", () => {
    it("builds an absolute webhook execution URL from namespace, id and key", () => {
        const url = webhookUrl({namespace: "company.team", id: "my-flow", key: "admin1234"})

        expect(url).toMatch(/^https?:\/\//)
        expect(url).toContain("/api/v1/main/executions/webhook/company.team/my-flow/admin1234")
    })

    it("URL-encodes path segments that contain special characters", () => {
        const url = webhookUrl({namespace: "company.team", id: "my flow", key: "key/with/slashes"})

        expect(url).toContain("/executions/webhook/company.team/my%20flow/key%2Fwith%2Fslashes")
    })

    it("exposes the canonical webhook trigger type", () => {
        expect(WEBHOOK_TRIGGER_TYPE).toBe("io.kestra.plugin.core.trigger.Webhook")
    })
})

describe("WEBHOOK_TRIGGER_TYPES", () => {
    it("contains only fully qualified Java class names", () => {
        const javaFqcnRegex = /^[a-z]+(\.[a-z0-9_]+)+\.[A-Z][a-zA-Z0-9_]*$/

        for (const type of WEBHOOK_TRIGGER_TYPES) {
            expect(type).toMatch(javaFqcnRegex)
        }
    })

    it("has no duplicate trigger types", () => {
        const unique = new Set(WEBHOOK_TRIGGER_TYPES)

        expect(unique.size).toBe(WEBHOOK_TRIGGER_TYPES.length)
    })

    it("includes the canonical webhook trigger type", () => {
        expect(WEBHOOK_TRIGGER_TYPES).toContain(WEBHOOK_TRIGGER_TYPE)
    })

    it("recognises every listed type as a webhook trigger", () => {
        for (const type of WEBHOOK_TRIGGER_TYPES) {
            expect(WEBHOOK_TRIGGER_TYPES.includes(type)).toBe(true)
        }
    })

    it("does not treat non-webhook trigger types as webhooks", () => {
        const nonWebhookTriggers = [
            "io.kestra.plugin.core.trigger.Schedule",
            "io.kestra.plugin.core.trigger.Flow",
            "io.kestra.plugin.kafka.RealtimeTrigger",
            "io.kestra.plugin.aws.sqs.RealtimeTrigger",
            "io.kestra.plugin.gcp.pubsub.RealtimeTrigger",
        ]

        for (const type of nonWebhookTriggers) {
            expect(WEBHOOK_TRIGGER_TYPES.includes(type)).toBe(false)
        }
    })
})
