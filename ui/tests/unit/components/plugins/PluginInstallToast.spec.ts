import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {flushPromises, mount, VueWrapper} from "@vue/test-utils"

import type {PluginInstallJob} from "../../../../src/stores/plugins"

const getInstallJobMock = vi.fn()
const findPluginByNameMock = vi.fn((_name: string): {title: string} | null => null)

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        getInstallJob: getInstallJobMock,
        findPluginByName: findPluginByNameMock,
        list: vi.fn(),
        plugins: [],
    }),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

const KsProgressStub = {
    name: "KsProgress",
    props: ["percentage", "strokeWidth"],
    template: "<div class='ks-progress-stub' />",
}

function job(status: PluginInstallJob["status"], overrides: Partial<PluginInstallJob> = {}): PluginInstallJob {
    return {
        id: "job-1",
        status,
        artifacts: [{groupId: "io.kestra.plugin", artifactId: "plugin-aws", extension: "jar", classifier: null, version: "1.0.0"}],
        progress: {},
        startedAt: null,
        finishedAt: null,
        error: null,
        ...overrides,
    }
}

async function mountToast(props: {onSuccess?: () => void; onFailure?: () => void} = {}) {
    const {default: PluginInstallToast} = await import("../../../../src/components/plugins/PluginInstallToast.vue")
    const wrapper = mount(PluginInstallToast, {
        props: {jobId: "job-1", ...props},
        global: {
            mocks: {$t: (key: string) => key},
            stubs: {KsIcon: true, KsSkeleton: true, KsText: {template: "<span><slot /></span>"}, KsProgress: KsProgressStub},
        },
    })
    await flushPromises()
    return wrapper
}

describe("PluginInstallToast", () => {
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        vi.useFakeTimers()
        getInstallJobMock.mockReset()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        wrapper?.unmount()
        vi.useRealTimers()
    })

    async function tick(times: number) {
        for (let i = 0; i < times; i++) {
            await vi.advanceTimersByTimeAsync(500)
            await flushPromises()
        }
    }

    it("calls onSuccess once the job succeeds", async () => {
        const onSuccess = vi.fn()
        const onFailure = vi.fn()
        getInstallJobMock
            .mockResolvedValueOnce(job("RUNNING"))
            .mockResolvedValue(job("SUCCEEDED"))

        wrapper = await mountToast({onSuccess, onFailure})
        await tick(1)

        expect(onSuccess).toHaveBeenCalledOnce()
        expect(onFailure).not.toHaveBeenCalled()
    })

    it("calls onFailure when the job fails", async () => {
        const onFailure = vi.fn()
        getInstallJobMock
            .mockResolvedValueOnce(job("RUNNING"))
            .mockResolvedValue(job("FAILED", {error: "boom"}))

        wrapper = await mountToast({onFailure})
        await tick(1)

        expect(onFailure).toHaveBeenCalledOnce()
        expect(wrapper.text()).toContain("boom")
    })

    it("tolerates transient poll failures instead of failing terminally", async () => {
        const onSuccess = vi.fn()
        const onFailure = vi.fn()
        getInstallJobMock
            .mockResolvedValueOnce(job("RUNNING"))
            .mockResolvedValueOnce(null)
            .mockResolvedValueOnce(null)
            .mockResolvedValue(job("SUCCEEDED"))

        wrapper = await mountToast({onSuccess, onFailure})
        await tick(3)

        expect(onFailure).not.toHaveBeenCalled()
        expect(onSuccess).toHaveBeenCalledOnce()
    })

    it("gives up after enough consecutive poll failures", async () => {
        const onFailure = vi.fn()
        getInstallJobMock
            .mockResolvedValueOnce(job("RUNNING"))
            .mockResolvedValue(null)

        wrapper = await mountToast({onFailure})
        await tick(10)

        expect(onFailure).toHaveBeenCalledOnce()
        // Polling stopped: no further requests after the failure
        const callsAtFailure = getInstallJobMock.mock.calls.length
        await tick(3)
        expect(getInstallJobMock.mock.calls.length).toBe(callsAtFailure)
    })

    it("shows the plugin's human title when the store knows it", async () => {
        findPluginByNameMock.mockImplementation((name: string) => name === "plugin-aws" ? {title: "Amazon Web Services"} : null)
        getInstallJobMock.mockResolvedValue(job("RUNNING"))

        wrapper = await mountToast()

        expect(wrapper.text()).toContain("Amazon Web Services")
    })

    it("matches each artifact's own progress entry, not a prefix-sharing sibling", async () => {
        const running = job("RUNNING", {
            artifacts: [
                {groupId: "io.kestra.plugin", artifactId: "plugin-aws", extension: "jar", classifier: null, version: "1.0.0"},
                {groupId: "io.kestra.plugin", artifactId: "plugin-aws-s3", extension: "jar", classifier: null, version: "1.0.0"},
            ],
            progress: {
                "io/kestra/plugin/plugin-aws-1.0.0.jar": {resource: "plugin-aws", transferred: 50, total: 100, state: "PROGRESSING"},
                "io/kestra/plugin/plugin-aws-s3-1.0.0.jar": {resource: "plugin-aws-s3", transferred: 25, total: 100, state: "PROGRESSING"},
            },
        })
        getInstallJobMock.mockResolvedValue(running)

        wrapper = await mountToast()

        const percentages = wrapper.findAllComponents(KsProgressStub).map(c => c.props("percentage"))
        expect(percentages).toEqual([50, 25])
    })
})
