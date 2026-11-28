import {afterEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {mount} from "@vue/test-utils"
import type {MiscControllerConfiguration} from "@kestra-io/kestra-sdk"
import CopilotMcpTip from "./CopilotMcpTip.vue"
import {mountGlobal} from "../../../../tests/unit/components/ai/copilot/_helpers"
import {storageKeys} from "../../../utils/constants"

const TIP = "[data-test=\"copilot-mcp-tip\"]"

const miscStore = reactive({configs: undefined as MiscControllerConfiguration | undefined})
vi.mock("override/stores/misc", () => ({useMiscStore: () => miscStore}))

describe("CopilotMcpTip", () => {
    afterEach(() => {
        localStorage.removeItem(storageKeys.COPILOT_MCP_TIP_DISMISSED)
        miscStore.configs = undefined
    })

    it("shows when the MCP server is enabled", () => {
        miscStore.configs = {isMcpServerEnabled: true}

        expect(mount(CopilotMcpTip, {global: mountGlobal}).find(TIP).exists()).toBe(true)
    })

    it("stays hidden when the MCP server is disabled", () => {
        miscStore.configs = {isMcpServerEnabled: false}

        expect(mount(CopilotMcpTip, {global: mountGlobal}).find(TIP).exists()).toBe(false)
    })

    it("stays hidden until the configs have loaded", async () => {
        const w = mount(CopilotMcpTip, {global: mountGlobal})
        expect(w.find(TIP).exists()).toBe(false)

        miscStore.configs = {isMcpServerEnabled: true}
        await w.vm.$nextTick()

        expect(w.find(TIP).exists()).toBe(true)
    })

    it("stays hidden on later mounts once dismissed", async () => {
        miscStore.configs = {isMcpServerEnabled: true}
        const w = mount(CopilotMcpTip, {global: mountGlobal})

        await w.find("[data-test=\"copilot-mcp-tip-dismiss\"]").trigger("click")

        expect(w.find(TIP).exists()).toBe(false)
        expect(mount(CopilotMcpTip, {global: mountGlobal}).find(TIP).exists()).toBe(false)
    })
})
