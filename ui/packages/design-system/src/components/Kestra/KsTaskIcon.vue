<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <img class="ks-task-icon__icon" :src="dataUri" :alt="ariaLabel">
        </KsTooltip>

        <img
            v-else
            class="ks-task-icon__icon"
            :src="dataUri"
            :alt="ariaLabel"
        >
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {cssVar} from "../../utils/css"
    import {useTheme} from "../../composables/useTheme"

    defineOptions({
        name: "KsTaskIcon",
    })

    export interface KsTaskIconData {
        icon: string;
        flowable: boolean;
    }

    const props = defineProps<{
        customIcon?: {icon: string};
        cls?: string;
        icons?: Record<string, KsTaskIconData>;
        onlyIcon?: boolean;
        variable?: string;
        /**
         * Lazily resolves the icon for `cls` when it isn't already present in `icons`, instead of
         * requiring the whole plugin-icons catalog to be preloaded. The caller is expected to cache
         * results (see `pluginsStore.loadIcon`) since several KsTaskIcon instances commonly ask for
         * the same class.
         */
        loadIcon?: (cls: string) => Promise<KsTaskIconData | undefined>;
    }>()

    const {isDark} = useTheme()

    const FALLBACK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
        "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
        "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
        "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
        "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
        "</svg>"

    function innerClassToParent(cls: string) {
        return cls.includes("$") ? cls.substring(0, cls.indexOf("$")) : cls
    }

    const providedIcon = computed<KsTaskIconData | undefined>(() => {
        if (!props.cls) {
            return props.customIcon as KsTaskIconData | undefined
        }

        return (props.icons ?? {})[innerClassToParent(props.cls)]
    })

    const lazyIcon = ref<KsTaskIconData>()

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

    const dataUri = computed(() => `data:image/svg+xml;base64,${imageBase64.value}`)

    const imageBase64 = computed(() => {
        void isDark.value

        let localIcon = icon.value?.icon ? window.atob(icon.value.icon) : FALLBACK_SVG

        const color = (props.variable ? cssVar(props.variable) : "") || cssVar("--ks-text-primary")
        localIcon = localIcon.replace(/currentColor/g, color)

        return window.btoa(localIcon)
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
            object-fit: contain;
            object-position: center center;
        }
    }
</style>
