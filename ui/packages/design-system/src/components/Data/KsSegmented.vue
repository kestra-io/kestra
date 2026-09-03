<template>
    <ElSegmented
        v-model="model"
        :class="props.disabled ? 'is-disabled' : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-bind="scope" />
        </template>
        <template v-else-if="hasIcons" #default="{item}">
            <span class="ks-segmented-item">
                <KsIcon v-if="iconOf(item)" :size="iconSize"><component :is="iconOf(item)" /></KsIcon>
                {{ labelOf(item) }}
            </span>
        </template>
    </ElSegmented>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {ElSegmented} from "element-plus"
    import KsIcon from "../Basic/KsIcon.vue"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number | boolean>()

    type SegmentedObjectOption = {label: string; value: string | number | boolean; disabled?: boolean; icon?: Component}

    const props = defineProps<{
        options?: Array<string | number | SegmentedObjectOption>
        size?: "large" | "default" | "small"
        disabled?: boolean
        block?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: string | number | boolean]
    }>()

    const filteredProps = useFilteredProps(props)

    // Only take over the item rendering when an icon is actually in play, so options without one
    // keep Element Plus's own markup.
    const hasIcons = computed(() => (props.options ?? []).some(option => typeof option === "object" && option.icon))

    // Element Plus types its slot item as its own `Option`, which knows nothing of our `icon`.
    const asObjectOption = (item: unknown): SegmentedObjectOption | undefined =>
        (typeof item === "object" && item !== null ? item as SegmentedObjectOption : undefined)

    const iconOf = (item: unknown): Component | undefined => asObjectOption(item)?.icon

    const labelOf = (item: unknown): unknown => asObjectOption(item)?.label ?? item

    // Keep the icon proportional to the control it sits in, rather than one fixed size.
    const iconSize = computed(() => {
        switch (props.size) {
        case "large": return "base"
        case "small": return "xs"
        default: return "sm"
        }
    })
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/segmented';

    .el-segmented__item-selected {
        font-weight: 500;
    }

    .ks-segmented-item {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    // In light theme --ks-bg-hover-elevated equals --ks-bg-base, so the track
    // background is invisible against the page; --ks-bg-elevated gives contrast.
    html:not(.dark) .el-segmented,
    html:not(.dark) .kel-segmented {
        --el-fill-color-light: var(--ks-bg-elevated);
        --kel-fill-color-light: var(--ks-bg-elevated);
    }

    .el-segmented.is-disabled .el-segmented__item-selected,
    .kel-segmented.is-disabled .kel-segmented__item-selected {
        background-color: var(--ks-bg-inactive);
    }

    .el-segmented.is-disabled .el-segmented__item.is-selected,
    .kel-segmented.is-disabled .kel-segmented__item.is-selected {
        color: var(--ks-text-secondary);
    }
</style>
