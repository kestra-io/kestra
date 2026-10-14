<template>
    <Teleport v-if="isMobile" defer to="#bottom-actions-slot">
        <div class="bottom-action-bar" role="toolbar" :aria-label="$t('actions')" data-test="bottom-action-bar">
            <div class="bottom-action-bar-main">
                <slot />
            </div>
            <div v-if="$slots.more" class="bottom-action-bar-more">
                <slot name="more" />
            </div>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
    import {useIsMobile} from "../../composables/useIsMobile"

    defineSlots<{
        default?(): unknown
        more?(): unknown
    }>()

    const isMobile = useIsMobile()
</script>

<style scoped lang="scss">
    .bottom-action-bar {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        padding-bottom: calc(var(--ks-spacing-3) + env(safe-area-inset-bottom));
        background: var(--ks-bg-surface);
        border-top: 1px solid var(--ks-border-default);
    }

    .bottom-action-bar-main {
        display: grid;
        flex: 1;
        grid-auto-flow: column;
        grid-auto-columns: minmax(0, 1fr);
        gap: var(--ks-spacing-2);
    }

    .bottom-action-bar-more {
        flex: none;
    }
</style>
