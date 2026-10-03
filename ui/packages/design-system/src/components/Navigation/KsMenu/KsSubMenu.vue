<template>
    <ElSubMenu :index="props.index" v-bind="{...filteredProps(), ...$attrs}">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
    </ElSubMenu>
</template>

<script setup lang="ts">
    import {ElSubMenu, type SubMenuProps} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<SubMenuProps>()

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["index"])
</script>
