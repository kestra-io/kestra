import {afterEach, describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotMcpTip from "./CopilotMcpTip.vue"
import {mountGlobal} from "../../../../tests/unit/components/ai/copilot/_helpers"
import {storageKeys} from "../../../utils/constants"

const TIP = "[data-test=\"copilot-mcp-tip\"]"

describe("CopilotMcpTip", () => {
    afterEach(() => localStorage.removeItem(storageKeys.COPILOT_MCP_TIP_DISMISSED))

    it("stays hidden on later mounts once dismissed", async () => {
        const w = mount(CopilotMcpTip, {global: mountGlobal})

        await w.find("[data-test=\"copilot-mcp-tip-dismiss\"]").trigger("click")

        expect(w.find(TIP).exists()).toBe(false)
        expect(mount(CopilotMcpTip, {global: mountGlobal}).find(TIP).exists()).toBe(false)
    })
})
