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
    it("every entry is a fully qualified trigger class name", () => {
        expect(WEBHOOK_TRIGGER_TYPES.length).toBeGreaterThan(0)
        for (const type of WEBHOOK_TRIGGER_TYPES) {
            expect(type).toMatch(/^io\.kestra\.plugin\.[a-z0-9_.]+\.[A-Za-z0-9]+$/)
        }
    })

    it("the list has no duplicates", () => {
        const unique = new Set(WEBHOOK_TRIGGER_TYPES)
        expect(unique.size).toBe(WEBHOOK_TRIGGER_TYPES.length)
    })

    it("includes canonical WEBHOOK_TRIGGER_TYPE", () => {
        expect(WEBHOOK_TRIGGER_TYPES).toContain(WEBHOOK_TRIGGER_TYPE)
    })

    it("a trigger type not in the list is not treated as a webhook", () => {
        expect(WEBHOOK_TRIGGER_TYPES.includes("io.kestra.plugin.core.trigger.Schedule")).toBe(false)
        expect(WEBHOOK_TRIGGER_TYPES.includes("io.kestra.plugin.core.trigger.Flow")).toBe(false)
    })
})
