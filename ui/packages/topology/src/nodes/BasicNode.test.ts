import {describe, expect, it, vi} from "vitest"
import {computed, defineComponent, h} from "vue"
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
import SkipNextCircleOutline from "vue-material-design-icons/SkipNextCircleOutline.vue"
import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue"
import ProgressClock from "vue-material-design-icons/ProgressClock.vue"
import Cancel from "vue-material-design-icons/Cancel.vue"
import RotatingDots from "../assets/icons/RotatingDots.vue"
import BasicNode from "./BasicNode.vue"
import {PILL_ZOOM_COMPENSATION_INJECTION_KEY} from "../injectionKeys"
import {PILL_COMPENSATION, ZOOM_LOD, pillZoomCompensation} from "../utils/constants"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const CLS = "io.kestra.plugin.core.log.Log"
const PILL = "[data-test=\"topology-task-pill\"]"
const PILL_STATUS = "[data-test=\"topology-task-pill-status\"]"

// Records the props the app-provided task-icon component is mounted with.
const iconProps: Record<string, unknown>[] = []
const TaskIconSpy = defineComponent({
    name: "TaskIconSpy",
    inheritAttrs: false,
    props: {cls: {type: String, default: undefined}, icons: {type: Object, default: undefined}, loadIcon: {type: Function, default: undefined}},
    setup(props) {
        iconProps.push({cls: props.cls, icons: props.icons, loadIcon: props.loadIcon})
        return () => h("img")
    },
})

/** `zoom` is the canvas zoom the node is asked to render at; it reaches the node as the quantised
 *  compensation factor Topology provides, exactly as it does at runtime. Left out, nothing is
 *  provided at all, which is how a node mounted outside a canvas sees it. */
function mountBasicNode(props: Record<string, unknown> = {}, slots: Record<string, string> = {}, zoom?: number) {
    return i18nMount(BasicNode, {
        props: {
            id: "root.my-task",
            data: {node: {task: {id: "my-task", type: CLS}}, color: "default"},
            icons: {},
            ...props,
        },
        global: {
            // KsTooltip wraps the title in an element-plus popper; render only its default slot.
            stubs: {KsTooltip: {template: "<span><slot /></span>"}},
            provide: {
                [TASK_ICON_INJECTION_KEY as symbol]: TaskIconSpy,
                ...(zoom === undefined ? {} : {[PILL_ZOOM_COMPENSATION_INJECTION_KEY as symbol]: computed(() => pillZoomCompensation(zoom))}),
            },
        },
        slots,
    })
}

describe("BasicNode icons", () => {
    it("should hand the task-icon component the loadIcon resolver so a class missing from the icons index can still be fetched", () => {
        // Given an icons index that doesn't carry this class — the plugin isn't in the local index
        iconProps.length = 0
        const loadIcon = vi.fn().mockResolvedValue(undefined)

        // When the node renders
        mountBasicNode({icons: {}, loadIcon})

        // Then the icon component gets the resolver, not just the empty index
        expect(iconProps).toHaveLength(1)
        expect(iconProps[0].cls).toBe(CLS)
        expect(iconProps[0].loadIcon).toBe(loadIcon)
    })

    it("should still render without a loadIcon resolver", () => {
        iconProps.length = 0

        mountBasicNode({icons: {[CLS]: {flowable: false, monochrome: false, hasIcon: true}}})

        expect(iconProps[0].loadIcon).toBeUndefined()
        expect(iconProps[0].icons).toHaveProperty(CLS)
    })
})

describe("BasicNode layout", () => {
    const slots = {
        subtitle: "<span class='subtitle-marker'>Log</span>",
        "title-status": "<span class='status-marker'>status</span>",
        "title-actions": "<span class='actions-marker'>actions</span>",
    }

    it("should render the subtitle under the title, inside the content column", () => {
        const wrapper = mountBasicNode({}, slots)

        expect(wrapper.find(".node-content > .node-subtitle > .subtitle-marker").exists()).toBe(true)
        expect(wrapper.find(".node-title .subtitle-marker").exists()).toBe(false)
    })

    it("should render the status and actions as direct children of the main content", () => {
        const wrapper = mountBasicNode({}, slots)

        expect(wrapper.find(".main-content > .status-marker").exists()).toBe(true)
        expect(wrapper.find(".main-content > .actions-marker").exists()).toBe(true)

        expect(wrapper.find(".node-content .status-marker").exists()).toBe(false)
        expect(wrapper.find(".node-content .actions-marker").exists()).toBe(false)
    })

    it("should render the footer inside the card, below the main content", () => {
        const wrapper = mountBasicNode({}, {...slots, footer: "<div class='footer-marker'>bar</div>"})

        expect(wrapper.find(".node-core > .main-content + .node-footer > .footer-marker").exists()).toBe(true)
        expect(wrapper.find(".node-core").classes()).toContain("node-core--with-footer")
    })

    it("should render no footer band when no footer is given", () => {
        const wrapper = mountBasicNode({}, slots)

        expect(wrapper.find(".node-footer").exists()).toBe(false)
        expect(wrapper.find(".node-core").classes()).not.toContain("node-core--with-footer")
    })
})

describe("BasicNode levels of detail (kestra-io/kestra#19666)", () => {
    const slots = {
        subtitle: "<span class='subtitle-marker'>Log</span>",
        "title-status": "<span class='status-marker'>status</span>",
        "title-actions": "<span class='actions-marker'>actions</span>",
        details: "<div class='details-marker'>details</div>",
        footer: "<div class='footer-marker'>bar</div>",
    }

    it("should collapse to the icon and its own status glyph below the pill threshold", () => {
        const wrapper = mountBasicNode({lod: "pill", state: "FAILED"}, slots)

        expect(wrapper.find(".node-pill").exists()).toBe(true)
        // The state survives as the pill's own glyph, while the card's `title-status` slot — a
        // live-ticking Duration and the validation badge — stays out, which is the cost the pill
        // tier exists to avoid paying on every node of a zoomed-out graph.
        expect(wrapper.find(PILL_STATUS).exists()).toBe(true)
        expect(wrapper.find(".status-marker").exists()).toBe(false)
        expect(wrapper.find(".subtitle-marker").exists()).toBe(false)
        expect(wrapper.find(".actions-marker").exists()).toBe(false)
        expect(wrapper.find(".footer-marker").exists()).toBe(false)
    })

    it("should render the full card at the default level, with no details overlay", () => {
        const wrapper = mountBasicNode({lod: "default"}, slots)

        expect(wrapper.find(".node-pill").exists()).toBe(false)
        expect(wrapper.find(".status-marker").exists()).toBe(true)
        expect(wrapper.find(".subtitle-marker").exists()).toBe(true)
        expect(wrapper.find(".details-marker").exists()).toBe(false)
        expect(wrapper.find(".node-footer .footer-marker").exists()).toBe(true)
    })

    it("should additionally render the details overlay above the expanded threshold", () => {
        const wrapper = mountBasicNode({lod: "expanded"}, slots)

        expect(wrapper.find(".status-marker").exists()).toBe(true)
        expect(wrapper.find(".node-details-overlay .details-marker").exists()).toBe(true)
        expect(wrapper.find(".node-footer .footer-marker").exists()).toBe(true)
    })
})

describe("BasicNode pill status (kestra-io/kestra#21619)", () => {
    // Every state, with SUCCESS and FAILED the pair the report turns on: telling a failed task
    // from a successful one is the whole point. The two carrying no background tint (SKIPPED, and
    // anything falling through to NEUTRAL such as WARNING) are the ones a colour-only indicator
    // would lose, so they are covered here rather than assumed.
    it.each([
        ["SUCCESS", CheckCircleOutline, "--ks-text-success"],
        ["FAILED", CloseCircleOutline, "--ks-text-error"],
        ["KILLED", CloseCircleOutline, "--ks-text-error"],
        ["RUNNING", RotatingDots, "--ks-status-running"],
        ["KILLING", StopCircleOutline, "--ks-status-pending"],
        ["SKIPPED", SkipNextCircleOutline, "--ks-status-neutral"],
        ["CANCELLED", Cancel, "--ks-status-neutral"],
        ["WARNING", ProgressClock, "--ks-status-neutral"],
    ])("should give a %s task a recognizable status glyph at the pill level", (state, icon, textVar) => {
        const wrapper = mountBasicNode({lod: "pill", state})

        const badge = wrapper.find(PILL_STATUS)
        expect(badge.exists()).toBe(true)
        expect(badge.attributes("style")).toContain(textVar)
        expect(badge.attributes("aria-label")).toBe(state)
        expect(wrapper.findComponent(icon).exists()).toBe(true)
    })

    it("should keep dimming a skipped task's plugin icon, as the full card does", () => {
        const wrapper = mountBasicNode({lod: "pill", state: "SKIPPED"})

        expect(wrapper.find(".node-pill .icon--dimmed").exists()).toBe(true)
    })

    it("should render no status glyph for a task that has not run", () => {
        const wrapper = mountBasicNode({lod: "pill"})

        expect(wrapper.find(".node-pill").exists()).toBe(true)
        expect(wrapper.find(PILL_STATUS).exists()).toBe(false)
    })
})

describe("BasicNode pill status at low zoom (kestra-io/kestra#21619)", () => {
    const ringWidthOf = (zoom: number) =>
        mountBasicNode({lod: "pill", state: "FAILED"}, {}, zoom).find(PILL).attributes("style")
    const glyphScaleOf = (zoom: number) =>
        mountBasicNode({lod: "pill", state: "FAILED"}, {}, zoom).find(PILL_STATUS).attributes("style")

    it("should widen the state ring as the canvas zooms out, so it survives in screen pixels", () => {
        // Graph-space width x zoom is what actually reaches the screen: a 1px ring is 0.29px at
        // fit-to-view, which is the sub-pixel ring the report describes as "just plugin icons".
        expect(ringWidthOf(ZOOM_LOD.PILL)).toContain("border-width: 2px")
        expect(ringWidthOf(0.29)).toContain("border-width: 3px")
        expect(ringWidthOf(0.25)).toContain("border-width: 4px")
    })

    it("should stop widening the ring at the cap, so a very large graph cannot grow a ring over its own pill", () => {
        expect(ringWidthOf(0.1)).toContain(`border-width: ${PILL_COMPENSATION.RING_MAX}px`)
        expect(ringWidthOf(0.02)).toContain(`border-width: ${PILL_COMPENSATION.RING_MAX}px`)
    })

    it("should leave the ring at its natural width once the cards are back", () => {
        expect(ringWidthOf(1)).toContain("border-width: 1px")
    })

    // The flow editor renders the same pills with no execution behind them, so there is no state
    // to make visible there and nothing to compensate for: widening their border would be a
    // restyling of the editor smuggled in through an execution fix.
    it("should leave a pill with no execution state at its authored width, however far out the canvas is", () => {
        for (const zoom of [ZOOM_LOD.PILL, 0.29, 0.02]) {
            const wrapper = mountBasicNode({lod: "pill"}, {}, zoom)

            expect(wrapper.find(PILL).exists()).toBe(true)
            expect(wrapper.find(PILL).attributes("style")).toBeUndefined()
        }
    })

    it("should enlarge the status glyph at low zoom but never past the cap that keeps it on its pill", () => {
        expect(glyphScaleOf(1)).toContain("scale(1)")
        expect(glyphScaleOf(0.29)).toContain(`scale(${PILL_COMPENSATION.GLYPH_MAX})`)
        // Still capped two thirds of the way to MIN_ZOOM, where the ring has long since capped too.
        expect(glyphScaleOf(0.02)).toContain(`scale(${PILL_COMPENSATION.GLYPH_MAX})`)
    })

    // One state of each kind the gate above has to tell apart: SUCCESS and FAILED carry a tint,
    // while SKIPPED and WARNING carry none, so their style object holds only undefined values. All
    // four have run, so all four are compensated; only a stateless pill is left alone.
    it.each(["SUCCESS", "FAILED", "SKIPPED", "WARNING"])("should compensate a %s task at fit-to-view zoom", (state) => {
        const wrapper = mountBasicNode({lod: "pill", state}, {}, 0.29)

        expect(wrapper.find(PILL).attributes("style")).toContain("border-width: 3px")
        expect(wrapper.find(PILL_STATUS).attributes("style")).toContain(`scale(${PILL_COMPENSATION.GLYPH_MAX})`)
    })

    it("should keep the status tint on the pill alongside the widened ring", () => {
        const style = mountBasicNode({lod: "pill", state: "FAILED"}, {}, 0.29).find(PILL).attributes("style")

        expect(style).toContain("--ks-topology-bg-errors")
        expect(style).toContain("--ks-topology-border-errors")
    })

    it("should scale nothing when no compensation is provided, so a node outside a canvas still renders", () => {
        const wrapper = mountBasicNode({lod: "pill", state: "FAILED"})

        expect(wrapper.find(PILL).attributes("style")).toContain("border-width: 1px")
        expect(wrapper.find(PILL_STATUS).attributes("style")).toContain("scale(1)")
    })
})
