<template>
    <template v-if="!loading">
        <slot v-if="secondaryCount <= MAX_SECONDARY_ACTIONS" />
        <NavBarActionsDropdown v-else>
            <slot />
        </NavBarActionsDropdown>
    </template>
    <slot name="primary" />
</template>

<script setup lang="ts">
    import {Comment, Fragment, Text, computed, useSlots, type VNode} from "vue"
    import NavBarActionsDropdown from "./NavBarActionsDropdown.vue"

    defineProps<{loading?: boolean}>()

    const MAX_SECONDARY_ACTIONS = 1

    const slots = useSlots()

    const flatten = (nodes: VNode[]): VNode[] =>
        nodes.flatMap((node) => {
            if (node.type === Fragment) {
                return flatten(Array.isArray(node.children) ? (node.children as VNode[]) : [])
            }
            if (node.type === Comment || node.type === Text) {
                return []
            }
            return [node]
        })

    const secondaryCount = computed(() => flatten(slots.default?.() ?? []).length)
</script>
