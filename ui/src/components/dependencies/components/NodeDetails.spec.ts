import {describe, expect, it, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import NodeDetails from "./NodeDetails.vue"
import en from "../../../translations/en.json"
import {NODE, ASSET, FLOW} from "../utils/types"
import type {Node} from "../utils/types"

const node = (subtype: typeof ASSET | typeof FLOW): Node => ({
    id: "vm",
    type: NODE,
    flow: "vm",
    metadata: {subtype, assetMetadata: {"system.tools": ["GCP", "Terraform"]}},
}) as Node

describe("NodeDetails tools", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it.each([
        {subtype: ASSET, tools: ["GCP", "Terraform"]},
        {subtype: FLOW, tools: []},
    ])("lists the tools of an asset only ($subtype)", ({subtype, tools}) => {
        const wrapper = i18nMount(NodeDetails, {locales: en, props: {node: node(subtype)}, global: {stubs: {Link: true, RouterLink: true}}})

        expect(wrapper.findAll("[data-test=node-tools] [data-test=asset-tool]").map((tool) => tool.text())).toEqual(tools)
    })
})
