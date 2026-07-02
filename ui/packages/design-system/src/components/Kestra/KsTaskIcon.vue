<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <div
                class="ks-task-icon__icon"
                role="img"
                :aria-label="ariaLabel"
                :style="iconStyle"
                v-html="svgMarkup"
            />
        </KsTooltip>

        <div
            v-else
            class="ks-task-icon__icon"
            role="img"
            :aria-label="ariaLabel"
            :style="iconStyle"
            v-html="svgMarkup"
        />
    </div>
</template>

<script lang="ts">
    // <defs>/<symbol>/<style> only matter through an id or class reference — they never render in
    // place — so they're hoisted once into a shared, hidden pool keyed by icon content, instead of
    // being re-declared in every instance. The actual visible shapes (paths, groups, ...) stay
    // inline per instance, exactly as authored, so the rendered markup is still directly readable
    // in the DOM rather than hidden behind a <use>-cloned shadow tree.
    const POOL_ID = "ks-task-icon-pool"
    const FALLBACK_KEY = "fallback"

    export interface KsTaskIconData {
        icon: string;
        flowable: boolean;
    }

    const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 384 512\">" +
        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/></svg>"

    function getPool(): Element {
        const existing = document.getElementById(POOL_ID)
        if (existing) {
            return existing
        }

        const pool = document.createElementNS("http://www.w3.org/2000/svg", "svg")
        pool.setAttribute("id", POOL_ID)
        pool.setAttribute("aria-hidden", "true")
        pool.setAttribute("style", "position:absolute;width:0;height:0;overflow:hidden")
        document.body.prepend(pool)
        return pool
    }

    // Non-cryptographic string hash (FNV-1a-ish) — good enough to key a bounded, first-party set of
    // plugin icons, not to defend against adversarial collisions. Prefixed with a letter because a
    // CSS id selector ("#123abc") is invalid when it starts with a digit.
    function hashKey(value: string): string {
        let hash = 0
        for (let i = 0; i < value.length; i++) {
            hash = (Math.imul(31, hash) + value.charCodeAt(i)) | 0
        }
        return `kti-${(hash >>> 0).toString(36)}`
    }

    /**
     * Icons ship inside third-party plugin JARs, so even though the backend strips executable
     * content before serving them (see SvgSanitizer), we defensively re-check here as well.
     */
    function sanitize(doc: Document): boolean {
        const root = doc.documentElement
        if (root.nodeName !== "svg" || doc.querySelector("parsererror")) {
            return false
        }

        doc.querySelectorAll("script, foreignObject").forEach(el => el.remove())
        doc.querySelectorAll("*").forEach(el => {
            Array.from(el.attributes).forEach(({name, value}) => {
                if (/^on/i.test(name) || /javascript:/i.test(value)) {
                    el.removeAttribute(name)
                }
            })
        })

        return true
    }

    function collectClassNames(svg: string): Set<string> {
        const classNames = new Set<string>()
        svg.replace(/\bclass="([^"]+)"/g, (match, classList) => {
            classList.split(/\s+/).forEach((name: string) => classNames.add(name))
            return match
        })
        return classNames
    }

    /**
     * Namespaces every `id` (and its `url(#id)` / `href="#id"` references) plus every CSS `class`
     * (and its `.class` selectors) to `key`, so gradients, clip-paths, `<use>` targets and — just as
     * important — generic tool-exported class names (Illustrator's "st0", Figma's "cls-1", reused
     * across dozens of unrelated icons) don't collide once several icons' ids/styles live in the
     * same document. Namespaced by icon (type), not by render instance — every instance of the same
     * icon reuses the same pooled defs.
     *
     * `classNames` must be collected from the *whole* icon up front (see collectClassNames): a
     * `<style>` block only ever contains `.class` selectors, never a `class="..."` attribute, so
     * namespacing it in isolation would never discover which class names to rename.
     */
    function namespace(svg: string, key: string, classNames: Set<string>): string {
        let out = svg.replace(/\bid="([^"]+)"|url\(#([^)]+)\)|href="#([^"]+)"/g, (match, id, urlRef, hrefRef) => {
            const target = id ?? urlRef ?? hrefRef
            if (id !== undefined) return `id="${target}-${key}"`
            if (urlRef !== undefined) return `url(#${target}-${key})`
            return `href="#${target}-${key}"`
        })

        out = out.replace(/\bclass="([^"]+)"/g, (match, classList) =>
            `class="${classList.split(/\s+/).map((name: string) => `${name}-${key}`).join(" ")}"`)

        for (const className of classNames) {
            const escaped = className.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
            out = out.replace(new RegExp(`\\.${escaped}\\b`, "g"), `.${className}-${key}`)
        }

        return out
    }

    function toViewBox(svg: SVGSVGElement): string | undefined {
        const viewBox = svg.getAttribute("viewBox")
        if (viewBox) return viewBox

        const width = Number(svg.getAttribute("width"))
        const height = Number(svg.getAttribute("height"))
        return width > 0 && height > 0 ? `0 0 ${width} ${height}` : undefined
    }

    interface ProcessedIcon {
        viewBox?: string;
        body: string;
    }

    const processedIcons = new Map<string, ProcessedIcon>()

    /**
     * Hoists this icon's <defs>/<symbol>/<style> elements into the shared pool (once per distinct
     * icon) and returns the remaining, still fully inline, visible markup to render per instance.
     */
    function ensureProcessed(rawIcon: string | undefined): ProcessedIcon {
        const key = rawIcon ? hashKey(rawIcon) : FALLBACK_KEY
        const cached = processedIcons.get(key)
        if (cached) {
            return cached
        }

        const raw = rawIcon ? window.atob(rawIcon) : FALLBACK_SVG
        const doc = new DOMParser().parseFromString(raw, "image/svg+xml")

        if (!sanitize(doc)) {
            const fallback = rawIcon ? ensureProcessed(undefined) : {body: ""}
            processedIcons.set(key, fallback)
            return fallback
        }

        const svg = doc.documentElement as unknown as SVGSVGElement
        const viewBox = toViewBox(svg)
        const classNames = collectClassNames(svg.innerHTML)

        const definitions = [...svg.querySelectorAll("defs, symbol, style")]
        if (definitions.length > 0) {
            const pool = getPool()
            if (!pool.querySelector(`[data-icon="${key}"]`)) {
                const markup = definitions.map(el => el.outerHTML).join("")
                pool.insertAdjacentHTML("beforeend", `<g data-icon="${key}">${namespace(markup, key, classNames)}</g>`)
            }
            definitions.forEach(el => el.remove())
        }

        const processed: ProcessedIcon = {viewBox, body: namespace(svg.innerHTML, key, classNames)}
        processedIcons.set(key, processed)
        return processed
    }
</script>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"

    defineOptions({
        name: "KsTaskIcon",
    })

    type IconData = KsTaskIconData

    const props = defineProps<{
        customIcon?: {icon: string};
        cls?: string;
        icons?: Record<string, IconData>;
        onlyIcon?: boolean;
        variable?: string;
        /**
         * Lazily resolves the icon for `cls` when it isn't already present in `icons`, instead of
         * requiring the whole plugin-icons catalog to be preloaded. The caller is expected to cache
         * results (see `pluginsStore.loadIcon`) since several KsTaskIcon instances commonly ask for
         * the same class.
         */
        loadIcon?: (cls: string) => Promise<IconData | undefined>;
    }>()

    function innerClassToParent(cls: string) {
        return cls.includes("$") ? cls.substring(0, cls.indexOf("$")) : cls
    }

    const providedIcon = computed<IconData | undefined>(() => {
        if (!props.cls) {
            return props.customIcon as IconData | undefined
        }

        return (props.icons ?? {})[innerClassToParent(props.cls)]
    })

    const lazyIcon = ref<IconData>()

    watch(() => props.cls, cls => {
        lazyIcon.value = undefined

        if (!cls || providedIcon.value || !props.loadIcon) {
            return
        }

        const requestedCls = cls
        props.loadIcon(innerClassToParent(cls)).then(result => {
            // discard stale responses if `cls` changed while the request was in flight
            if (requestedCls === props.cls) {
                lazyIcon.value = result
            }
        })
    }, {immediate: true})

    const icon = computed(() => providedIcon.value ?? lazyIcon.value)

    const ariaLabel = computed(() => props.cls ?? "icon")

    const classes = computed(() => ({
        "ks-task-icon--flowable": icon.value?.flowable ?? false,
    }))

    const iconStyle = computed(() => ({
        color: `var(${props.variable || "--ks-text-primary"})`,
    }))

    const svgMarkup = computed(() => {
        const {viewBox, body} = ensureProcessed(icon.value?.icon)
        const viewBoxAttr = viewBox ? ` viewBox="${viewBox}"` : ""
        return `<svg width="100%" height="100%" focusable="false" aria-hidden="true"${viewBoxAttr}>${body}</svg>`
    })
</script>

<style lang="scss" scoped>
    .ks-task-icon {
        display: inline-block;
        width: 100%;
        height: 100%;
        position: relative;

        :deep(span) {
            position: absolute;
            padding: 1px;
            left: 0;
            display: block;
            width: 100%;
            height: 100%;
        }

        &__icon {
            width: 100%;
            height: 100%;
            display: block;
            border-radius: 3px;
        }
    }
</style>
