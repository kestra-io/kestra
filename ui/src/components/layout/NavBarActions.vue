<template>
    <template v-if="hasSecondarySlot">
        <NavBarActionsDropdown v-if="overflowCount > 0">
            <slot />
        </NavBarActionsDropdown>
        <slot name="secondary" />
    </template>
    <template v-else>
        <slot v-if="overflowCount <= MAX_INLINE_ACTIONS" />
        <NavBarActionsDropdown v-else>
            <slot />
        </NavBarActionsDropdown>
    </template>
    <slot name="primary" />
</template>

<script setup lang="ts">
    import {Comment, Fragment, Text, computed, useSlots, type VNode} from "vue"
    import NavBarActionsDropdown from "./NavBarActionsDropdown.vue"

    const MAX_INLINE_ACTIONS = 1

    const slots = useSlots()

    // Keyed on slot declaration rather than rendered content: a `#secondary` whose own `v-if`
    // is false must not fall back to the legacy branch and start rendering overflow inline.
    const hasSecondarySlot = computed(() => !!slots.secondary)

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

    const overflowCount = computed(() => flatten(slots.default?.() ?? []).length)
</script>
