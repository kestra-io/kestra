import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {beforeEach, describe, expect, test, vi} from "vitest"
import {i18nShallowMount} from "../../tests/unit/i18nMount"

const pdfMocks = vi.hoisted(() => ({
    getDocument: vi.fn(),
}))

vi.mock("pdfjs-dist", () => ({
    GlobalWorkerOptions: {},
    getDocument: pdfMocks.getDocument,
}))

import PdfPreview from "./PdfPreview.vue"

interface Deferred<T> {
    promise: Promise<T>
    resolve: (value: T | PromiseLike<T>) => void
    reject: (reason?: unknown) => void
}

const deferred = <T>(): Deferred<T> => {
    let resolve!: (value: T | PromiseLike<T>) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise
        reject = rejectPromise
    })
    return {promise, resolve, reject}
}

const pdfDocument = {
    numPages: 3,
    getPage: vi.fn(),
}

const renderPromises = new Map<number, Deferred<void>>()

interface MountedPreview {
    wrapper: VueWrapper
    documentPromise: Deferred<typeof pdfDocument>
}

const mountPreview = (documentPromise = deferred<typeof pdfDocument>()): MountedPreview => {
    pdfMocks.getDocument.mockReturnValue({promise: documentPromise.promise})
    return {
        wrapper: i18nShallowMount(PdfPreview, {
            props: {source: "JVBERi0xLjQ="},
            messages: {
                page: {
                    previous: "Previous",
                    next: "Next",
                },
                of: "of",
            },
            global: {
                stubs: {
                    KsTooltip: {template: "<div><slot /></div>"},
                    KsButton: {template: "<button><slot /></button>"},
                },
            },
        }),
        documentPromise,
    }
}

const startDocument = async (mounted: MountedPreview): Promise<void> => {
    mounted.documentPromise.resolve(pdfDocument)
    await flushPromises()
}

const resolveRender = async (page: number): Promise<void> => {
    renderPromises.get(page)?.resolve()
    await flushPromises()
}

beforeEach(() => {
    pdfMocks.getDocument.mockReset()
    pdfDocument.getPage.mockReset()
    renderPromises.clear()

    pdfDocument.getPage.mockImplementation((page: number) => Promise.resolve({
        getViewport: () => ({height: 100, width: 100}),
        render: () => {
            const renderPromise = deferred<void>()
            renderPromises.set(page, renderPromise)
            return {promise: renderPromise.promise}
        },
    }))

    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
        {} as unknown as CanvasRenderingContext2D,
    )
})

describe("PdfPreview", () => {
    test("hides navigation until the first page has rendered", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)

        expect(mounted.wrapper.find("nav").exists()).toBe(false)

        await resolveRender(1)

        expect(mounted.wrapper.find("nav").exists()).toBe(true)
    })

    test("shows the current page and page count after the first page renders", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)

        expect(mounted.wrapper.find("nav").text()).toContain("1")
        expect(mounted.wrapper.find("nav").text()).toContain("of")
        expect(mounted.wrapper.find("nav").text()).toContain("3")
    })

    test("does nothing when the previous button is clicked on page one", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)

        await mounted.wrapper.findAll("button")[0].trigger("click")

        expect(pdfDocument.getPage).toHaveBeenCalledTimes(1)
        expect(mounted.wrapper.find("nav").text()).toContain("1")
    })

    test("does nothing when the next button is clicked on the last page", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)

        await mounted.wrapper.findAll("button")[1].trigger("click")
        await resolveRender(2)
        await mounted.wrapper.findAll("button")[1].trigger("click")
        await resolveRender(3)

        await mounted.wrapper.findAll("button")[1].trigger("click")

        expect(pdfDocument.getPage).toHaveBeenCalledTimes(3)
        expect(mounted.wrapper.find("nav").text()).toContain("3")
    })

    test("renders a page when paging within range", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)

        await mounted.wrapper.findAll("button")[1].trigger("click")

        expect(pdfDocument.getPage).toHaveBeenCalledWith(2)
        expect(mounted.wrapper.find("nav").text()).toContain("2")

        await resolveRender(2)

        expect(mounted.wrapper.find("nav").text()).toContain("2")
    })

    test("renders the previous page when paging back within range", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)
        await mounted.wrapper.findAll("button")[1].trigger("click")
        await resolveRender(2)

        await mounted.wrapper.findAll("button")[0].trigger("click")

        expect(pdfDocument.getPage).toHaveBeenLastCalledWith(1)
        expect(mounted.wrapper.find("nav").text()).toContain("1")
    })

    test("queues a page requested while rendering and renders it after the current page finishes", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)

        await mounted.wrapper.findAll("button")[1].trigger("click")
        await mounted.wrapper.findAll("button")[1].trigger("click")

        expect(pdfDocument.getPage).not.toHaveBeenCalledWith(3)

        await resolveRender(2)

        expect(pdfDocument.getPage).toHaveBeenLastCalledWith(3)
        expect(pdfDocument.getPage).toHaveBeenCalledTimes(3)
        expect(mounted.wrapper.find("nav").text()).toContain("3")
    })

    test("renders only the last page from multiple queued requests", async () => {
        const mounted = mountPreview()
        await startDocument(mounted)
        await resolveRender(1)

        await mounted.wrapper.findAll("button")[1].trigger("click")
        await mounted.wrapper.findAll("button")[0].trigger("click")
        await mounted.wrapper.findAll("button")[1].trigger("click")

        await resolveRender(2)

        expect(pdfDocument.getPage).toHaveBeenCalledWith(2)
        expect(pdfDocument.getPage).toHaveBeenNthCalledWith(2, 2)
        expect(pdfDocument.getPage).toHaveBeenNthCalledWith(3, 2)
        expect(pdfDocument.getPage).toHaveBeenCalledTimes(3)
        expect(mounted.wrapper.find("nav").text()).toContain("2")

        await resolveRender(2)

        expect(pdfDocument.getPage).toHaveBeenCalledTimes(3)
    })

    test("logs a document load failure and keeps navigation hidden", async () => {
        const error = new Error("Cannot load PDF")
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
        const documentPromise = deferred<typeof pdfDocument>()
        const mounted = mountPreview(documentPromise)
        documentPromise.reject(error)
        await flushPromises()

        expect(consoleError).toHaveBeenCalledWith("Failed to render PDF", error)
        expect(mounted.wrapper.find("nav").exists()).toBe(false)

        consoleError.mockRestore()
    })
})
