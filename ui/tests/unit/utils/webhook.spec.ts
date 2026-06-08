import {describe, expect, it} from "vitest"
import {webhookUrl, WEBHOOK_TRIGGER_TYPE} from "../../../src/utils/webhook"

describe("webhookUrl", () => {
    it("builds an absolute webhook execution URL from namespace, id and key", () => {
        const url = webhookUrl({namespace: "company.team", id: "my-flow", key: "admin1234"})

        expect(url).toMatch(/^https?:\/\//)
        expect(url).toContain("/api/v1/main/executions/webhook/company.team/my-flow/admin1234")
    })

    it("exposes the canonical webhook trigger type", () => {
        expect(WEBHOOK_TRIGGER_TYPE).toBe("io.kestra.plugin.core.trigger.Webhook")
    })
})
