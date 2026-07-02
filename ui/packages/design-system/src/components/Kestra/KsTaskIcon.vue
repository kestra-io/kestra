<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <span
                class="ks-task-icon__icon"
                role="img"
                :aria-label="ariaLabel"
                :style="iconStyle"
                v-html="svgMarkup"
            />
        </KsTooltip>

        <span
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
    // module scope (shared across every instance) — used to namespace svg ids, see namespaceIds()
    let instanceSeq = 0

    export interface KsTaskIconData {
        icon: string;
        flowable: boolean;
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

    const instanceUid = `kti${instanceSeq++}`

    const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 384 512\">" +
        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/></svg>"

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

    /**
     * Namespaces every `id` in the SVG (and its `url(#id)` / `href="#id"` references) to the
     * current component instance so that gradients, clip-paths and `<use>` targets don't collide
     * when the same icon is inlined more than once on a page.
     */
    function namespaceIds(svg: string): string {
        return svg.replace(/\bid="([^"]+)"|url\(#([^)]+)\)|href="#([^"]+)"/g, (match, id, urlRef, hrefRef) => {
            const target = id ?? urlRef ?? hrefRef
            if (id !== undefined) return `id="${target}-${instanceUid}"`
            if (urlRef !== undefined) return `url(#${target}-${instanceUid})`
            return `href="#${target}-${instanceUid}"`
        })
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

    const svgMarkup = computed(() => {
        const raw = icon.value?.icon ? window.atob(icon.value.icon) : FALLBACK_SVG

        const doc = new DOMParser().parseFromString(raw, "image/svg+xml")
        if (!sanitize(doc)) {
            return FALLBACK_SVG
        }

        const svg = doc.documentElement
        svg.setAttribute("width", "100%")
        svg.setAttribute("height", "100%")
        svg.setAttribute("focusable", "false")
        // the accessible name lives on the wrapper (role="img" + aria-label) above
        svg.setAttribute("aria-hidden", "true")

        return namespaceIds(svg.outerHTML)
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
