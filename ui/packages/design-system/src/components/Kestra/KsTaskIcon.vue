<template>
    <div
        :class="classes"
        class="ks-task-icon"
    >
        <KsTooltip v-if="!onlyIcon" :content="cls">
            <img class="ks-task-icon__icon" :src="src" alt="" />
        </KsTooltip>

        <img v-else class="ks-task-icon__icon" :src="src" :alt="cls ?? ''" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {useTheme} from "../../composables/useTheme"
    import {cssVar} from "../../utils/css"
    import {getTaskIconSrc} from "../../utils/taskIconSrc"

    defineOptions({
        name: "KsTaskIcon",
    })

    const props = defineProps<{
        customIcon?: {icon: string};
        cls?: string;
        icons?: Record<string, {icon: string; flowable: boolean}>;
        onlyIcon?: boolean;
    }>()

    // cssVar() reads getComputedStyle synchronously and isn't reactive on its own; referencing
    // isDark.value here forces this computed to re-run — and re-bake the icon's color — whenever
    // the theme toggles, since --ks-text-primary's resolved value changes with it.
    const {isDark} = useTheme()

    const classes = computed(() => {
        return {
            "ks-task-icon--flowable": icon.value && "flowable" in icon.value ? icon.value.flowable : false,
        }
    })

    const src = computed(() => {
        void isDark.value
        return getTaskIconSrc(icon.value?.icon, cssVar("--ks-text-primary"))
    })

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
        object-fit: contain;
    }
</style>
