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
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElIcon>
</template>

<script setup lang="ts">
    import {ElIcon, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        size?: number | string
        color?: string,
        tooltip?: string;
        placement?: string;
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>
