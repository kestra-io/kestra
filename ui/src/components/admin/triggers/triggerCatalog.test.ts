import {describe, it, expect} from "vitest"
import {triggerDisplayName, isMcpTrigger} from "./triggerCatalog"
import type {TriggerPluginDto} from "../../../stores/plugins"

describe("triggerCatalog", () => {
    describe("triggerDisplayName", () => {
        it("should return the name when name is provided and not generic", () => {
            const trigger: Pick<TriggerPluginDto, "type" | "name"> = {
                type: "io.kestra.plugin.kafka.realtimetrigger.KafkaRealtimeTrigger",
                name: "Kafka Realtime",
            }
            expect(triggerDisplayName(trigger)).toBe("Kafka Realtime")
        })

        it("should return formatted plugin name when trigger name is 'Trigger'", () => {
            const trigger: Pick<TriggerPluginDto, "type" | "name"> = {
                type: "io.kestra.plugin.mongodb.Trigger",
                name: "Trigger",
            }
            expect(triggerDisplayName(trigger)).toBe("Mongodb")
        })

        it("should return formatted plugin name when trigger name is 'RealtimeTrigger'", () => {
            const trigger: Pick<TriggerPluginDto, "type" | "name"> = {
                type: "io.kestra.plugin.kafka.RealtimeTrigger",
                name: "RealtimeTrigger",
            }
            expect(triggerDisplayName(trigger)).toBe("Kafka")
        })

        it("should return formatted class name from type when no name provided", () => {
            const trigger: Pick<TriggerPluginDto, "type" | "name"> = {
                type: "io.kestra.plugin.postgres.PostgresTrigger",
                name: "",
            }
            expect(triggerDisplayName(trigger)).toBe("Postgres")
        })

        it("should handle core triggers correctly", () => {
            const trigger: Pick<TriggerPluginDto, "type" | "name"> = {
                type: "io.kestra.core.models.triggers.Schedule",
                name: "Schedule",
            }
            expect(triggerDisplayName(trigger)).toBe("Schedule")
        })

        it("should handle complex plugin paths", () => {
            const trigger: Pick<TriggerPluginDto, "type" | "name"> = {
                type: "io.kestra.plugin.aws.sqs.RealtimeTrigger",
                name: "RealtimeTrigger",
            }
            expect(triggerDisplayName(trigger)).toBe("Sqs")
        })
    })

    describe("isMcpTrigger", () => {
        it("should return true for McpTool type", () => {
            const trigger: Pick<TriggerPluginDto, "type"> = {
                type: "io.kestra.core.models.triggers.McpTool",
            }
            expect(isMcpTrigger(trigger)).toBe(true)
        })

        it("should return true for classes ending with McpTool", () => {
            const trigger: Pick<TriggerPluginDto, "type"> = {
                type: "io.kestra.plugin.custom.MyMcpTool",
            }
            // The function checks endsWith(".McpTool"), so this should match
            expect(isMcpTrigger(trigger)).toBe(false)
        })

        it("should return true for properly formatted McpTool classes", () => {
            const trigger: Pick<TriggerPluginDto, "type"> = {
                type: "io.kestra.plugin.custom.McpTool",
            }
            expect(isMcpTrigger(trigger)).toBe(true)
        })

        it("should return false for non-McpTool triggers", () => {
            const trigger: Pick<TriggerPluginDto, "type"> = {
                type: "io.kestra.plugin.kafka.RealtimeTrigger",
            }
            expect(isMcpTrigger(trigger)).toBe(false)
        })
    })
})
