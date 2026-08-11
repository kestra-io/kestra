<template>
    <!--
        Keyed on slot declaration rather than rendered content, so a caller whose `#secondary`
        content is `v-if`-ed away still gets the two-slot layout instead of silently falling back
        to the legacy one. Read straight off `$slots` in the template so it is re-evaluated on
        every render: a `computed(() => !!slots.secondary)` never registers a dependency (it checks
        for the key without invoking the slot) and would freeze at its first-render value for a
        caller that gates the `<template #secondary>` declaration itself.
    -->
    <template v-if="$slots.secondary">
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
