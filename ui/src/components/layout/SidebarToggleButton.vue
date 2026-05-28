<template>
    <KsIconButton class="collapseButton sidebar-toggle" @click="$emit('toggle')" aria-label="Toggle menu">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 20 16" fill="none">
            <path
                d="M18 0H2C1.46957 0 0.960859 0.210714 0.585786 0.585786C0.210714 0.960859 0 1.46957 0 2V14C0 14.5304 0.210714 15.0391 0.585786 15.4142C0.960859 15.7893 1.46957 16 2 16H18C18.5304 16 19.0391 15.7893 19.4142 15.4142C19.7893 15.0391 20 14.5304 20 14V2C20 1.46957 19.7893 0.960859 19.4142 0.585786C19.0391 0.210714 18.5304 0 18 0ZM18 14H7V2H18V14Z"
                fill="white" />
        </svg>
    </KsIconButton>
</template>

<script setup lang="ts">
    import {KsIconButton} from "@kestra-io/design-system"

    defineEmits<{
        (e: "toggle"): void;
    }>()
</script>


<style scoped lang="scss">
.sidebar-toggle {
    border: none;
    color: var(--ks-text-secondary);

    html.dark & {
        color: var(--ks-text-secondary);
    }
}
</style>