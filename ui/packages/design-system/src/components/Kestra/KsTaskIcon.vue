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
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {getSvgIcon, nextInstanceId} from "../../utils/svgIcon"

    defineOptions({
        name: "KsTaskIcon",
    })

    const props = defineProps<{
        customIcon?: {icon: string};
        cls?: string;
        icons?: Record<string, {icon: string; flowable: boolean}>;
        onlyIcon?: boolean;
    }>()

    const instanceId = nextInstanceId()

    const classes = computed(() => {
        return {
            "ks-task-icon--flowable": icon.value && "flowable" in icon.value ? icon.value.flowable : false,
        }
    })

    const svgIcon = computed(() => getSvgIcon(icon.value?.icon, instanceId))

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
