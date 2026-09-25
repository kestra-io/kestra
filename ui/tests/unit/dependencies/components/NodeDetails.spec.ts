import {describe, it, expect, vi} from "vitest"
import {i18nMount} from "../../i18nMount"
import NodeDetails from "../../../../src/components/dependencies/components/NodeDetails.vue"
import {ASSET, FLOW} from "../../../../src/components/dependencies/utils/types"

vi.mock("vue-router", () => ({useRoute: () => ({params: {tenant: "test"}})}))

describe("NodeDetails", () => {
    it("offers to load more relations only when the node is a collapsed asset, and emits expand on click", async () => {
        const wrapper = i18nMount(NodeDetails, {
            props: {
                node: {
                    id: "hub", type: "NODE", flow: "hub",
                    metadata: {subtype: ASSET, collapsed: true, totalDegree: 50001},
                },
            },
            messages: {
                dependency: {dag: {hub: {notice: "{count} relations", expand: "Load more relations"}}},
            },
        })

        const button = wrapper.find("[data-test=\"expand-hub\"]")
        expect(button.exists()).toBe(true)

        await button.trigger("click")
        expect(wrapper.emitted("expand")).toHaveLength(1)
    })

    it("shows nothing extra for a node that is not collapsed", () => {
        const wrapper = i18nMount(NodeDetails, {
            props: {node: {id: "small", type: "NODE", flow: "small", metadata: {subtype: ASSET}}},
        })

        expect(wrapper.find("[data-test=\"expand-hub\"]").exists()).toBe(false)
    })

    it("shows the collapsed notice but no expand button for a non-asset node, since it has no /dependencies route of its own", () => {
        const wrapper = i18nMount(NodeDetails, {
            props: {
                node: {
                    id: "big-flow", type: "NODE", flow: "big-flow",
                    metadata: {subtype: FLOW, collapsed: true, totalDegree: 12000},
                },
            },
            messages: {
                dependency: {dag: {hub: {notice: "{count} relations"}}},
            },
        })

        expect(wrapper.text()).toContain("12000")
        expect(wrapper.find("[data-test=\"expand-hub\"]").exists()).toBe(false)
    })
})
