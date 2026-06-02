<template>
    <KsIconButton class="collapseButton sidebar-toggle" @click="$emit('toggle')" aria-label="Toggle menu">
        <DockLeft />
    </KsIconButton>
</template>

<script setup lang="ts">
    import {KsIconButton} from "@kestra-io/design-system"
    import DockLeft from "vue-material-design-icons/DockLeft.vue"

    defineEmits<{
        (e: "toggle"): void;
    }>()
</script>


<style scoped lang="scss">
.sidebar-toggle {
    color: var(--ks-icon-muted);
}
</style>