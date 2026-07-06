<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <img v-if="!isMonochrome" class="ks-task-icon__icon" :src="iconSrc" :alt="ariaLabel">
            <div v-else class="ks-task-icon__icon ks-task-icon__icon--mask" role="img" :aria-label="ariaLabel" :style="maskStyle" />
        </KsTooltip>

        <template v-else>
            <img v-if="!isMonochrome" class="ks-task-icon__icon" :src="iconSrc" :alt="ariaLabel">
            <div v-else class="ks-task-icon__icon ks-task-icon__icon--mask" role="img" :aria-label="ariaLabel" :style="maskStyle" />
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {cssVar} from "../../utils/css"
    import fallbackIcon from "../../assets/images/plugin-icon-fallback.svg"

    defineOptions({
        name: "KsTaskIcon",
    })

    export interface KsTaskIconData {
        flowable: boolean;
        monochrome: boolean;
        /**
         * Whether this class actually ships an icon. Every registered task/trigger class gets an
         * entry in the `icons` map regardless (other consumers rely on `flowable` being present
         * even without one), so this is the only reliable signal to fall back to the generic icon
         * instead of pointing at a `/svg` URL that would 404.
         */
        hasIcon: boolean;
    }

    const props = defineProps<{
        customIcon?: {icon: string; monochrome?: boolean};
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

    function innerClassToParent(cls: string) {
        return cls.includes("$") ? cls.substring(0, cls.indexOf("$")) : cls
    }

    const resolvedCls = computed(() => props.cls ? innerClassToParent(props.cls) : undefined)

    const providedIcon = computed<KsTaskIconData | undefined>(() => {
        if (!resolvedCls.value) {
            return undefined
        }

        return (props.icons ?? {})[resolvedCls.value]
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

    // Real, browser-cacheable SVG resource — no more client-side base64 decode/recolor/encode on
    // every reactive change. KESTRA_BASE_PATH keeps this correct when the app is served behind a
    // reverse-proxy path prefix.
    const iconUrl = computed(() => {
        if (!resolvedCls.value) {
            return undefined
        }

        // Trim a trailing slash so a root base path ("/") doesn't produce a leading "//", which
        // browsers parse as a protocol-relative URL (host "api") instead of an absolute path.
        const basePath = ((window as unknown as {KESTRA_BASE_PATH?: string}).KESTRA_BASE_PATH ?? "").replace(/\/$/, "")
        return `${basePath}/api/v1/plugins/icons/${encodeURIComponent(resolvedCls.value)}/icon.svg`
    })

    const isMonochrome = computed(() => {
        if (props.customIcon) {
            return props.customIcon.monochrome ?? false
        }

        return icon.value?.monochrome ?? false
    })

    const iconSrc = computed(() => {
        if (props.customIcon) {
            return props.customIcon.icon
        }

        return icon.value?.hasIcon ? iconUrl.value : fallbackIcon
    })

    const maskStyle = computed(() => ({
        maskImage: `url(${iconSrc.value})`,
        WebkitMaskImage: `url(${iconSrc.value})`,
        backgroundColor: (props.variable ? cssVar(props.variable) : "") || cssVar("--ks-text-primary"),
    }))
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

            &--mask {
                mask-repeat: no-repeat;
                mask-position: center;
                mask-size: contain;
                -webkit-mask-repeat: no-repeat;
                -webkit-mask-position: center;
                -webkit-mask-size: contain;
            }
        }
    }
</style>
