<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <div class="ks-task-icon__icon" :style="iconStyle" aria-hidden="true" v-html="sanitizedSvg" />
        </KsTooltip>

        <div v-else class="ks-task-icon__icon" :style="iconStyle" role="img" :aria-label="cls" v-html="sanitizedSvg" />
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
        theme?: "dark" | "light";
        icons?: Record<string, {icon: string; flowable: boolean}>;
        onlyIcon?: boolean;
        variable?: string;
    }>()

    const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
        "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
        "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
        "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
        "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
        "</svg>"

    // Icon strings are stable per plugin class, so sanitize each unique icon once per session.
    const sanitizedCache = new Map<string, string>()

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

    function sanitize(rawBase64: string | undefined): string {
        const cacheKey = rawBase64 ?? "__fallback__"
        const cached = sanitizedCache.get(cacheKey)
        if (cached !== undefined) {
            return cached
        }

        const rawSvg = rawBase64 ? window.atob(rawBase64) : FALLBACK_SVG
        const sanitized = DOMPurify.sanitize(ensureViewBox(rawSvg), {USE_PROFILES: {svg: true, svgFilters: true}})

        sanitizedCache.set(cacheKey, sanitized)
        return sanitized
    }

    const classes = computed(() => {
        return {
            "ks-task-icon--flowable": icon.value && "flowable" in icon.value ? icon.value.flowable : false,
        }
    })

    const iconStyle = computed(() => {
        return props.variable ? {color: `var(${props.variable})`} : undefined
    })

    const sanitizedSvg = computed(() => sanitize(icon.value?.icon))

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

    .ks-task-icon__icon svg {
        width: 100%;
        height: 100%;
        display: block;
    }
</style>
