import {describe, expect, it} from "vitest"
import {webhookArtifactProvider} from "./webhookArtifact"
import type {ArtifactBlock} from "./editorArtifacts"
import {WEBHOOK_TRIGGER_TYPE} from "../../../utils/webhook"

const t = (key: string) => key

const webhookBlock: ArtifactBlock = {
    type: WEBHOOK_TRIGGER_TYPE,
    value: {id: "webhook", type: WEBHOOK_TRIGGER_TYPE, key: "admin1234"},
    range: [40, 80, 80],
    path: "triggers",
}

describe("webhookArtifactProvider", () => {
    it("returns no lens when the flow has no namespace or id", () => {
        expect(webhookArtifactProvider.provide(webhookBlock, {namespace: "company.team", t})).toEqual([])
        expect(webhookArtifactProvider.provide(webhookBlock, {id: "my-flow", t})).toEqual([])
    })
})
