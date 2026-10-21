import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, fn, userEvent, within} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"
import NodeDetails from "../../../../src/components/dependencies/components/NodeDetails.vue"
import {ASSET, EXECUTION, FLOW} from "../../../../src/components/dependencies/utils/types"

const meta: Meta<typeof NodeDetails> = {
    title: "Components/Dependencies/NodeDetails",
    component: NodeDetails,
    decorators: [
        vueRouter([
            {path: "/", name: "home", component: {template: "<div />"}},
            {path: "/assets/:assetId", name: "assets/update", component: {template: "<div />"}},
            {path: "/flows/:namespace/:id", name: "flows/update", component: {template: "<div />"}},
            {path: "/executions/:namespace/:flowId/:id", name: "executions/update", component: {template: "<div />"}},
        ]),
    ],
    args: {onExpand: fn(), onClose: fn()},
    render: (args) => ({
        components: {NodeDetails},
        setup() {
            return {args}
        },
        template: "<div style=\"width:320px\"><NodeDetails v-bind=\"args\" /></div>",
    }),
}

export default meta

type Story = StoryObj<typeof NodeDetails>

export const CollapsedHub: Story = {
    args: {
        node: {
            id: "hub", type: "NODE", flow: "hub",
            metadata: {subtype: ASSET, collapsed: true, totalDegree: 50001, expandable: true},
        },
    },
    async play({canvasElement, args}) {
        const canvas = within(canvasElement)

        await expect(canvas.getByText(/50001 relations/)).toBeVisible()
        await userEvent.click(canvasElement.querySelector("[data-test='expand-hub']")!)

        await expect(args.onExpand).toHaveBeenCalledTimes(1)
    },
}

export const LoadingHub: Story = {
    args: {
        loading: true,
        node: {
            id: "hub", type: "NODE", flow: "hub",
            metadata: {subtype: ASSET, collapsed: true, totalDegree: 50001, expandable: true},
        },
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector("[data-test='expand-hub']")).toBeDisabled()
    },
}

export const ExhaustedHub: Story = {
    args: {
        node: {
            id: "hub", type: "NODE", flow: "hub",
            metadata: {subtype: ASSET, collapsed: true, totalDegree: 61, expandable: false, exhausted: true},
        },
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(canvas.queryByText(/61 relations/)).toBeNull()
        await expect(canvasElement.querySelector("[data-test='expand-hub']")).toBeNull()
    },
}

export const NotCollapsed: Story = {
    args: {
        node: {id: "small", type: "NODE", flow: "small", metadata: {subtype: ASSET}},
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(canvasElement.querySelector("[data-test='expand-hub']")).toBeNull()
        await expect(canvas.queryByText(/relations/)).toBeNull()
    },
}

export const CollapsedFlow: Story = {
    args: {
        node: {
            id: "big-flow", type: "NODE", flow: "big-flow", namespace: "company.team",
            metadata: {subtype: FLOW, collapsed: true, totalDegree: 12000, expandable: true},
        },
    },
    async play({canvasElement, args}) {
        const canvas = within(canvasElement)

        await expect(canvas.getByText(/12000 relations/)).toBeVisible()
        await userEvent.click(canvasElement.querySelector("[data-test='expand-hub']")!)

        await expect(args.onExpand).toHaveBeenCalledTimes(1)
    },
}

export const CollapsedExecution: Story = {
    args: {
        node: {
            id: "exec-hub", type: "NODE", flow: "exec-hub", namespace: "company.team",
            metadata: {subtype: EXECUTION, collapsed: true, totalDegree: 500, expandable: true},
        },
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(canvas.getByText(/500 relations/)).toBeVisible()
        await expect(canvasElement.querySelector("[data-test='expand-hub']")).toBeNull()
    },
}

export const CollapsedAnonymizedAsset: Story = {
    args: {
        node: {
            id: "t_ns_anon-uid", type: "NODE", flow: "t_ns_anon-uid",
            metadata: {subtype: ASSET, collapsed: true, totalDegree: 9001},
        },
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(canvas.getByText(/9001 relations/)).toBeVisible()
        await expect(canvasElement.querySelector("[data-test='expand-hub']")).toBeNull()
    },
}
