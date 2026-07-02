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
    // Icons are inlined via a shared <symbol> pool instead of duplicating full markup into every
    // instance: the same task/trigger icon commonly repeats dozens of times on one page (execution
    // timelines, task lists, ...), and re-parsing/re-sanitizing/re-serializing it per instance is
    // wasted work. The pool is a single hidden <svg>, appended once, holding one <symbol> per
    // distinct icon; each KsTaskIcon instance just renders a tiny `<use>` referencing it.
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

    /**
     * Namespaces every `id` in the SVG (and its `url(#id)` / `href="#id"` references) to `key` so
     * that gradients, clip-paths and `<use>` targets don't collide with another icon's once both
     * live in the shared pool. Namespaced by icon (type), not by render instance — every instance
     * of the same icon reuses the same pooled `<symbol>`.
     */
    function namespaceIds(svg: string, key: string): string {
        return svg.replace(/\bid="([^"]+)"|url\(#([^)]+)\)|href="#([^"]+)"/g, (match, id, urlRef, hrefRef) => {
            const target = id ?? urlRef ?? hrefRef
            if (id !== undefined) return `id="${target}-${key}"`
            if (urlRef !== undefined) return `url(#${target}-${key})`
            return `href="#${target}-${key}"`
        })
    }

    function toViewBox(svg: SVGSVGElement): string | undefined {
        const viewBox = svg.getAttribute("viewBox")
        if (viewBox) return viewBox

        const width = Number(svg.getAttribute("width"))
        const height = Number(svg.getAttribute("height"))
        return width > 0 && height > 0 ? `0 0 ${width} ${height}` : undefined
    }

    /**
     * Ensures a <symbol> for this icon exists in the shared pool, creating it on first use, and
     * returns the id to <use> it by.
     */
    function ensureSymbol(rawIcon: string | undefined): string {
        const key = rawIcon ? hashKey(rawIcon) : FALLBACK_KEY
        const pool = getPool()
        if (pool.querySelector(`#${key}`)) {
            return key
        }

        const raw = rawIcon ? window.atob(rawIcon) : FALLBACK_SVG
        const doc = new DOMParser().parseFromString(raw, "image/svg+xml")

        if (!sanitize(doc)) {
            return rawIcon ? ensureSymbol(undefined) : FALLBACK_KEY
        }

        const svg = doc.documentElement as unknown as SVGSVGElement
        const viewBox = toViewBox(svg)
        const inner = namespaceIds(svg.innerHTML, key)
        pool.insertAdjacentHTML(
            "beforeend",
            `<symbol id="${key}"${viewBox ? ` viewBox="${viewBox}"` : ""}>${inner}</symbol>`,
        )

        return key
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
        const symbolId = ensureSymbol(icon.value?.icon)
        return "<svg width=\"100%\" height=\"100%\" focusable=\"false\" aria-hidden=\"true\">" +
            `<use href="#${symbolId}" width="100%" height="100%"></use></svg>`
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
