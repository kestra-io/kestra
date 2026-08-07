import {describe, test, expect, vi, beforeEach} from "vitest"
import {shallowMount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const filePreviewMock = vi.fn()
const fileContentMock = vi.fn()
const fileMetaMock = vi.fn()

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({filePreview: filePreviewMock, fileContent: fileContentMock}),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    fileMetadatasFromExecution: (...args: unknown[]) => fileMetaMock(...args),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {preview: {initial: 500}}}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

import FilePreview from "../../../../src/components/executions/FilePreview.vue"

const FULL_HTML = "<html><body><h1>Hi</h1><script>document.title='ok'</script></body></html>"
const BIG_SIZE = 11 * 1024 * 1024  // 11 MB — over the 10 MB threshold
const SMALL_SIZE = 1024             // 1 KB — under threshold

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            download: "Download",
            loading: "Loading...",
            file_preview: {
                big_file_warning: "File is large ({size})",
                load_anyway: "Load anyway",
                load_error: "Failed to load the file preview.",
                retry: "Retry",
                html_asset_warning: "Local assets will not load.",
                html_preview_title: "HTML file preview",
            },
        },
    },
})

const globalConfig = {
    plugins: [i18n],
    stubs: {
        KsAlert: {template: "<div class=\"ks-alert-stub\"><slot /></div>"},
        KsButton: {template: "<button><slot /></button>"},
        KsButtonGroup: {template: "<div><slot /></div>"},
        KsTag: {template: "<span><slot /></span>"},
        KsText: {template: "<span><slot /></span>"},
        FilePreviewForm: true,
        RawPreview: true,
    },
}

function mountPreview(path: string) {
    return shallowMount(FilePreview, {
        props: {path, executionId: "exec-1"},
        global: globalConfig,
    })
}

describe("FilePreview — HTML path", () => {
    beforeEach(() => {
        filePreviewMock.mockReset()
        fileContentMock.mockReset()
        fileMetaMock.mockReset()
    })

    test("renders an iframe with srcdoc (not src) holding the full file for .html files", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        fileContentMock.mockResolvedValue(FULL_HTML)

        const wrapper = mountPreview("kestra:///outputs/report.html")
        await flushPromises()

        const iframe = wrapper.find("iframe")
        expect(iframe.exists()).toBe(true)
        expect(iframe.attributes("srcdoc")).toBe(FULL_HTML)
        // Must NOT use src — the /file endpoint sets Content-Disposition:attachment
        // which triggers a download prompt instead of inline rendering.
        expect(iframe.attributes("src")).toBeUndefined()
    })

    test("fetches the complete file via fileContent, never the truncated filePreview endpoint", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        fileContentMock.mockResolvedValue(FULL_HTML)

        mountPreview("kestra:///outputs/chart.html")
        await flushPromises()

        expect(fileContentMock).toHaveBeenCalledWith(
            expect.objectContaining({executionId: "exec-1", path: "kestra:///outputs/chart.html"}),
        )
        // filePreview truncates by rows/bytes — it must not be used for the iframe.
        expect(filePreviewMock).not.toHaveBeenCalled()
    })

    test("renders the sandboxed iframe with allow-scripts but no allow-same-origin", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        fileContentMock.mockResolvedValue(FULL_HTML)

        const wrapper = mountPreview("kestra:///outputs/report.html")
        await flushPromises()

        const sandbox = wrapper.find("iframe").attributes("sandbox")
        expect(sandbox).toBe("allow-scripts")
        expect(sandbox).not.toContain("allow-same-origin")
    })

    test("shows the local-asset limitation note above the iframe", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        fileContentMock.mockResolvedValue(FULL_HTML)

        const wrapper = mountPreview("kestra:///outputs/report.html")
        await flushPromises()

        expect(wrapper.text()).toContain("Local assets will not load.")
    })

    test("also renders the iframe for the .htm extension", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        fileContentMock.mockResolvedValue(FULL_HTML)

        const wrapper = mountPreview("kestra:///outputs/page.htm")
        await flushPromises()

        expect(wrapper.find("iframe").exists()).toBe(true)
    })

    test("shows the big-file warning for HTML files >= 10 MB and does not fetch content", async () => {
        fileMetaMock.mockResolvedValue({size: BIG_SIZE})

        const wrapper = mountPreview("kestra:///outputs/heavy.html")
        await flushPromises()

        expect(wrapper.find("iframe").exists()).toBe(false)
        expect(wrapper.find(".big-file-warning").exists()).toBe(true)
        expect(fileContentMock).not.toHaveBeenCalled()
    })

    test("renders an empty iframe for a zero-byte HTML file without fetching content", async () => {
        fileMetaMock.mockResolvedValue({size: 0})

        const wrapper = mountPreview("kestra:///outputs/empty.html")
        await flushPromises()

        const iframe = wrapper.find("iframe")
        expect(iframe.exists()).toBe(true)
        expect(iframe.attributes("srcdoc")).toBe("")
        expect(fileContentMock).not.toHaveBeenCalled()
    })

    test("non-HTML files render no iframe and use the preview endpoint", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        filePreviewMock.mockResolvedValue({content: "hello world", type: "RAW", truncated: false})

        const wrapper = mountPreview("kestra:///outputs/data.txt")
        await flushPromises()

        expect(wrapper.find("iframe").exists()).toBe(false)
        expect(filePreviewMock).toHaveBeenCalled()
        expect(fileContentMock).not.toHaveBeenCalled()
    })

    test("shows an error with retry instead of an endless loading state when the fetch fails", async () => {
        fileMetaMock.mockResolvedValue({size: SMALL_SIZE})
        fileContentMock.mockRejectedValueOnce(new Error("network"))

        const wrapper = mountPreview("kestra:///outputs/report.html")
        await flushPromises()

        // No iframe, no perpetual "Loading..." — an actionable error is shown.
        expect(wrapper.find("iframe").exists()).toBe(false)
        expect(wrapper.find(".load-error").exists()).toBe(true)
        expect(wrapper.text()).not.toContain("Loading...")

        // Retry re-fetches; a subsequent success renders the iframe.
        fileContentMock.mockResolvedValueOnce(FULL_HTML)
        await wrapper.find(".load-error button").trigger("click")
        await flushPromises()

        expect(wrapper.find(".load-error").exists()).toBe(false)
        expect(wrapper.find("iframe").attributes("srcdoc")).toBe(FULL_HTML)
    })

    test("does not crash at render when the path prop is undefined", async () => {
        // A backend rejects a request with no path — the realistic outcome for a
        // caller that mounts FilePreview before path is set.
        fileMetaMock.mockRejectedValue(new Error("no path"))

        // isHtmlFile runs during render (v-if), so an unguarded props.path.toLowerCase()
        // would throw synchronously on mount here.
        const wrapper = shallowMount(FilePreview, {
            props: {path: undefined as unknown as string, executionId: "exec-1"},
            global: globalConfig,
        })
        await flushPromises()

        expect(wrapper.find("iframe").exists()).toBe(false)
        expect(wrapper.find(".load-error").exists()).toBe(true)
    })
})
