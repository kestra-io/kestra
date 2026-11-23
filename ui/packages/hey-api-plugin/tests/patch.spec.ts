import {describe, expect, it} from "vitest"

import {fixYamlSourceRequestBodyContentType} from "../src/patch"

const stringSchema = {schema: {type: "string"}}

const operationWith = (content: Record<string, unknown>) => ({requestBody: {content}})

const apply = (operation: unknown) => {
    fixYamlSourceRequestBodyContentType("put", "/api/v1/{tenant}/flows", operation)
    return operation as {requestBody: {content: Record<string, unknown>}}
}

describe("fixYamlSourceRequestBodyContentType", () => {
    it("should drop the JSON variant and resolve YAML first for a legacy application/x-yaml body", () => {
        const operation = apply(operationWith({
            "application/json": {...stringSchema},
            "application/x-yaml": {...stringSchema},
            "text/plain": {...stringSchema},
        }))

        expect(Object.keys(operation.requestBody.content)).toEqual(["application/x-yaml", "text/plain"])
    })

    it("should drop the JSON variant and normalize a migrated application/yaml body to application/x-yaml", () => {
        const operation = apply(operationWith({
            "application/json": {...stringSchema},
            "application/yaml": {...stringSchema},
            "text/plain": {...stringSchema},
        }))

        expect(Object.keys(operation.requestBody.content)).toEqual(["application/x-yaml", "text/plain"])
    })

    it("should collapse a body declaring both YAML media types to the canonical one", () => {
        const operation = apply(operationWith({
            "application/json": {...stringSchema},
            "application/yaml": {...stringSchema},
            "application/x-yaml": {...stringSchema},
        }))

        expect(Object.keys(operation.requestBody.content)).toEqual(["application/x-yaml"])
    })

    it("should normalize a YAML-only body even with no JSON variant to drop", () => {
        const operation = apply(operationWith({"application/yaml": {...stringSchema}}))

        expect(Object.keys(operation.requestBody.content)).toEqual(["application/x-yaml"])
    })

    it("should keep the declared schema when normalizing the media type", () => {
        const operation = apply(operationWith({
            "application/json": {...stringSchema},
            "application/yaml": {schema: {type: "string", description: "The flow source"}},
        }))

        expect(operation.requestBody.content["application/x-yaml"]).toEqual({
            schema: {type: "string", description: "The flow source"},
        })
    })

    it("should leave a JSON-only body untouched", () => {
        const operation = apply(operationWith({"application/json": {schema: {$ref: "#/components/schemas/Flow"}}}))

        expect(Object.keys(operation.requestBody.content)).toEqual(["application/json"])
    })

    it("should ignore a YAML body whose schema is not a plain string", () => {
        const operation = apply(operationWith({
            "application/json": {...stringSchema},
            "application/yaml": {schema: {type: "string", format: "binary"}},
        }))

        expect(Object.keys(operation.requestBody.content)).toEqual(["application/json", "application/yaml"])
    })

    it("should not throw on an operation without a request body", () => {
        expect(() => apply({responses: {}})).not.toThrow()
    })
})
