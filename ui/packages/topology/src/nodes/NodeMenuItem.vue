<template>
    <KsDropdownItem
        :divided="action.divided"
        :icon="action.icon"
        :class="{'node-action--danger': action.danger}"
        @click="action.onClick()"
    >
        {{ action.label }}
    </KsDropdownItem>
</template>

<script setup lang="ts">
    import {KsDropdownItem} from "@kestra-io/design-system"
    import type {NodeAction} from "./NodeMenu.vue"

    defineOptions({name: "NodeMenuItem"})

    defineProps<{action: NodeAction}>()
</script>

<style scoped lang="scss">
.node-action--danger {
    color: var(--ks-text-error);
}
</style>
