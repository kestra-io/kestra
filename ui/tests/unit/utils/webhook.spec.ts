import {describe, expect, it} from "vitest"
import {webhookUrl, WEBHOOK_TRIGGER_TYPE} from "../../../src/utils/webhook"

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
