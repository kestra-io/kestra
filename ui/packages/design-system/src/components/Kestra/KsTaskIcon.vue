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
    // Icons are rendered fully inline, per instance — no shared/pooled defs. A shared pool was
    // tried, but <defs>/<symbol>/<style> content commonly depends on the *current* CSS context
    // (currentColor, custom properties) to render — a <linearGradient> stop using
    // stop-color="currentColor" resolves against whatever element it physically lives under, so a
    // gradient shared in an off-screen pool ignores every instance's own `variable`/theme color and
    // renders identically (and wrongly) everywhere. Keeping each icon's defs inside its own
    // instance's subtree is what makes per-instance recoloring correct.
    let instanceSeq = 0

    export interface KsTaskIconData {
        icon: string;
        flowable: boolean;
    }

    const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 384 512\">" +
        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/></svg>"

    // Non-cryptographic string hash (FNV-1a-ish) — used only to key the sanitized-markup cache
    // below, not for uniqueness guarantees.
    function hashKey(value: string): string {
        let hash = 0
        for (let i = 0; i < value.length; i++) {
            hash = (Math.imul(31, hash) + value.charCodeAt(i)) | 0
        }
        return (hash >>> 0).toString(36)
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
     * across dozens of unrelated icons) don't collide once several instances are inlined on the same
     * page. Namespaced per render instance, since each instance keeps its own private copy.
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

    // Parsing/sanitizing is the expensive part and gives the same result for the same icon
    // regardless of which instance asks for it, so it's cached; namespacing is cheap (plain regex)
    // and runs per instance since its result must NOT be shared (see note above).
    const sanitizedCache = new Map<string, string>()

    function sanitizedMarkup(rawIcon: string | undefined): string {
        const key = rawIcon ? hashKey(rawIcon) : "fallback"
        const cached = sanitizedCache.get(key)
        if (cached !== undefined) {
            return cached
        }

        const raw = rawIcon ? window.atob(rawIcon) : FALLBACK_SVG
        const doc = new DOMParser().parseFromString(raw, "image/svg+xml")

        if (!sanitize(doc)) {
            const fallback = rawIcon ? sanitizedMarkup(undefined) : FALLBACK_SVG
            sanitizedCache.set(key, fallback)
            return fallback
        }

        const svg = doc.documentElement as unknown as SVGSVGElement
        const viewBox = toViewBox(svg)
        if (viewBox) svg.setAttribute("viewBox", viewBox)
        svg.setAttribute("width", "100%")
        svg.setAttribute("height", "100%")
        svg.setAttribute("focusable", "false")
        svg.setAttribute("aria-hidden", "true")

        const markup = svg.outerHTML
        sanitizedCache.set(key, markup)
        return markup
    }
</script>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"

    defineOptions({
        name: "KsTaskIcon",
    })

    type IconData = KsTaskIconData

    const instanceUid = `kti${instanceSeq++}`

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
        const markup = sanitizedMarkup(icon.value?.icon)
        return namespace(markup, instanceUid, collectClassNames(markup))
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
