import {describe, expect, it} from "vitest"
import {unwrapEventStreamArrayResponses} from "../../../packages/hey-api-plugin/src/patch"

const eventRef = {$ref: "#/components/schemas/EventExecution"}

const operationWith = (schema: unknown, mediaType = "text/event-stream") => ({
    operationId: "followExecution",
    responses: {
        200: {
            description: "followExecution 200 response",
            content: {[mediaType]: {schema}},
        },
    },
})

describe("unwrapEventStreamArrayResponses", () => {
    it("replaces an array event-stream schema with its item schema", () => {
        const operation = operationWith({type: "array", items: eventRef})

        unwrapEventStreamArrayResponses("get", "/executions/{id}/follow", operation)

        expect(operation.responses[200].content["text/event-stream"].schema).toEqual(eventRef)
    })

    it("leaves an event-stream response already declared as a single event untouched", () => {
        const operation = operationWith(eventRef)

        unwrapEventStreamArrayResponses("get", "/executions/{id}/follow", operation)

        expect(operation.responses[200].content["text/event-stream"].schema).toEqual(eventRef)
    })

    it("does not touch array responses of other media types", () => {
        const operation = operationWith({type: "array", items: eventRef}, "application/json")

        unwrapEventStreamArrayResponses("get", "/executions/search", operation)

        expect(operation.responses[200].content["application/json"].schema).toEqual({type: "array", items: eventRef})
    })

    it("ignores operations without responses or content", () => {
        expect(() => unwrapEventStreamArrayResponses("get", "/ping", {})).not.toThrow()
        expect(() => unwrapEventStreamArrayResponses("get", "/ping", {responses: {200: {}}})).not.toThrow()
        expect(() => unwrapEventStreamArrayResponses("get", "/ping", undefined)).not.toThrow()
    })
})
