<template>
    <div
        :class="classes"
        class="task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <img v-if="!isMonochrome" class="task-icon__icon" :src="iconSrc" :alt="ariaLabel">
            <div v-else class="task-icon__icon task-icon__icon--mask" role="img" :aria-label="ariaLabel" :style="maskStyle" />
        </KsTooltip>

        <template v-else>
            <img v-if="!isMonochrome" class="task-icon__icon" :src="iconSrc" :alt="ariaLabel">
            <div v-else class="task-icon__icon task-icon__icon--mask" role="img" :aria-label="ariaLabel" :style="maskStyle" />
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsTooltip, cssVar} from "@kestra-io/design-system"
    import fallbackIcon from "../../assets/plugins/plugin-icon-fallback.svg"

    defineOptions({
        name: "TaskIcon",
    })

    export interface TaskIconData {
        flowable: boolean;
        monochrome: boolean;
        hasIcon: boolean;
        iconUrl?: string;
        hash?: string;
    }

    const props = defineProps<{
        customIcon?: {icon: string; monochrome?: boolean};
        cls?: string;
        icons?: Record<string, TaskIconData>;
        onlyIcon?: boolean;
        variable?: string;
        loadIcon?: (cls: string) => Promise<TaskIconData | undefined>;
    }>()

    function innerClassToParent(cls: string) {
        return cls.includes("$") ? cls.substring(0, cls.indexOf("$")) : cls
    }

    const resolvedCls = computed(() => props.cls ? innerClassToParent(props.cls) : undefined)

    const providedIcon = computed<TaskIconData | undefined>(() => {
        if (!resolvedCls.value) {
            return undefined
        }

        return (props.icons ?? {})[resolvedCls.value]
    })

    const lazyIcon = ref<TaskIconData>()

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
        "task-icon--flowable": icon.value?.flowable ?? false,
    }))

    const localIconUrl = computed(() => {
        if (!resolvedCls.value) {
            return undefined
        }

        const basePath = ((window as unknown as {KESTRA_BASE_PATH?: string}).KESTRA_BASE_PATH ?? "").replace(/\/$/, "")
        const base = `${basePath}/api/v1/plugins/icons/${encodeURIComponent(resolvedCls.value)}/icon.svg`
        return icon.value?.hash ? `${base}?v=${encodeURIComponent(icon.value.hash)}` : base
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

        if (icon.value?.iconUrl) {
            return icon.value.iconUrl
        }

        return icon.value?.hasIcon ? localIconUrl.value : fallbackIcon
    })

    const maskStyle = computed(() => ({
        maskImage: `url(${iconSrc.value})`,
        WebkitMaskImage: `url(${iconSrc.value})`,
        backgroundColor: (props.variable ? cssVar(props.variable) : "") || cssVar("--ks-text-primary"),
    }))
</script>

<style lang="scss" scoped>
    .task-icon {
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
