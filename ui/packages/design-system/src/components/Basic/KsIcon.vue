<template>
    <KsTooltip
        v-if="tooltip"
        :content="tooltip"
        :rawContent="true"
        v-bind="placement ? {placement} : {}"
        :enterable="false"
    >
        <ElIcon
            v-bind="({...filteredProps(), ...$attrs} as any)"
            :size="resolvedSize"
            @click="emit('click', $event)"
        >
            <template v-if="$slots.default" #default>
                <slot />
            </template>
        </ElIcon>
    </KsTooltip>
    <ElIcon
        v-else
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :size="resolvedSize"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElIcon>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {ElIcon} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    type IconSizeToken = "xs" | "sm" | "base" | "lg" | "xl"
    const SIZE_TOKENS: readonly IconSizeToken[] = ["xs", "sm", "base", "lg", "xl"] as const

    const props = defineProps<{
        size?: number | string | IconSizeToken
        color?: string
        tooltip?: string
        placement?: string
    }>()

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["size"])

    const resolvedSize = computed(() => {
        const s = props.size
        if (typeof s === "string" && (SIZE_TOKENS as readonly string[]).includes(s)) {
            return `var(--ks-icon-size-${s})`
        }
        return s
    })
</script>
