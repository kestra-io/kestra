<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <svg class="ks-task-icon__icon" v-bind="svgIcon.attrs" aria-hidden="true" v-html="svgIcon.innerHtml" />
        </KsTooltip>

        <svg v-else class="ks-task-icon__icon" v-bind="svgIcon.attrs" role="img" :aria-label="cls" v-html="svgIcon.innerHtml" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import DOMPurify from "dompurify"
    import KsTooltip from "../Feedback/KsTooltip.vue"

    defineOptions({
        name: "KsTaskIcon",
    })

    const props = defineProps<{
        customIcon?: {icon: string};
        cls?: string;
        icons?: Record<string, {icon: string; flowable: boolean}>;
        onlyIcon?: boolean;
    }>()

    const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
        "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
        "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
        "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
        "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
        "</svg>"

    // Attributes we render ourselves — never take these from the source icon markup.
    const EXCLUDED_ATTRS = new Set(["class", "style", "role", "aria-hidden", "aria-label", "width", "height", "xmlns"])

    interface SvgIcon {
        attrs: Record<string, string>;
        innerHtml: string;
    }

    // Icon strings are stable per plugin class, so sanitize/parse each unique icon once per session.
    const svgIconCache = new Map<string, SvgIcon>()

    function ensureViewBox(svg: string): string {
        const svgTagMatch = svg.match(/<svg\b[^>]*>/i)
        if (!svgTagMatch || /\sviewBox=/i.test(svgTagMatch[0])) {
            return svg
        }

        const widthMatch = svgTagMatch[0].match(/\swidth="([\d.]+)/i)
        const heightMatch = svgTagMatch[0].match(/\sheight="([\d.]+)/i)
        if (!widthMatch || !heightMatch) {
            return svg
        }

        const svgTagWithViewBox = svgTagMatch[0].replace(/<svg\b/i, `<svg viewBox="0 0 ${widthMatch[1]} ${heightMatch[1]}"`)
        return svg.replace(svgTagMatch[0], svgTagWithViewBox)
    }

    // Parses the sanitized markup so the root <svg>'s own attributes (viewBox, preserveAspectRatio, …)
    // can be bound directly onto the <svg> element we render — no wrapping <div> is needed.
    function toSvgIcon(rawBase64: string | undefined): SvgIcon {
        const cacheKey = rawBase64 ?? "__fallback__"
        const cached = svgIconCache.get(cacheKey)
        if (cached !== undefined) {
            return cached
        }

        const rawSvg = rawBase64 ? window.atob(rawBase64) : FALLBACK_SVG
        const sanitized = DOMPurify.sanitize(ensureViewBox(rawSvg), {USE_PROFILES: {svg: true, svgFilters: true}})

        const parsed = new DOMParser().parseFromString(sanitized, "image/svg+xml")
        const svgEl = parsed.documentElement

        let result: SvgIcon = {attrs: {}, innerHtml: ""}
        if (svgEl.tagName.toLowerCase() === "svg" && !parsed.querySelector("parsererror")) {
            const attrs: Record<string, string> = {}
            for (const attr of Array.from(svgEl.attributes)) {
                if (!EXCLUDED_ATTRS.has(attr.name)) {
                    attrs[attr.name] = attr.value
                }
            }
            result = {attrs, innerHtml: svgEl.innerHTML}
        }

        svgIconCache.set(cacheKey, result)
        return result
    }

    const classes = computed(() => {
        return {
            "ks-task-icon--flowable": icon.value && "flowable" in icon.value ? icon.value.flowable : false,
        }
    })

    const svgIcon = computed(() => toSvgIcon(icon.value?.icon))

    const icon = computed(() => {
        return props.cls ? (props.icons ?? {})[innerClassToParent(props.cls)] : props.customIcon
    })

    function innerClassToParent(cls: string) {
        return cls.includes("$") ? cls.substring(0, cls.indexOf("$")) : cls
    }
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
    }

    .ks-task-icon__icon {
        width: 100%;
        height: 100%;
        display: block;
        border-radius: 3px;
        color: var(--ks-text-primary);
    }
</style>
