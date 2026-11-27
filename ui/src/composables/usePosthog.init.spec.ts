import {describe, it, expect, vi, beforeEach} from "vitest"

const posthogMock = {
    __loaded: false,
    init: vi.fn(),
    register_for_session: vi.fn(),
    get_property: vi.fn(() => "alias"),
    alias: vi.fn(),
}

vi.mock("posthog-js", () => ({default: posthogMock}))
vi.mock("../stores/api", () => ({
    useApiStore: () => ({loadConfig: async () => ({id: "instance", posthog: {token: "phc_test", apiHost: "https://ph.test"}})}),
}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {}})}))
vi.mock("../utils/uid", () => ({ensureUid: () => "uid", getUid: () => "uid"}))

import {initPostHogForSetup} from "./usePosthog"

describe("initPostHogForSetup", () => {
    beforeEach(() => vi.clearAllMocks())

    it("captures clicks on links and buttons only, without their text", async () => {
        await initPostHogForSetup({uuid: "instance", version: "1.0.0"})

        expect(posthogMock.init).toHaveBeenCalledWith("phc_test", expect.objectContaining({
            api_host: "https://ph.test",
            autocapture: {dom_event_allowlist: ["click"], element_allowlist: ["a", "button"]},
            mask_all_text: true,
            capture_exceptions: true,
            capture_dead_clicks: true,
            rageclick: true,
            capture_performance: {web_vitals: true},
        }))
    })
})
