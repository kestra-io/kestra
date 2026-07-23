<template>
    <RouterLink :to="to" :title="value" class="ks-entity-link" @click.stop>
        <component :is="icon" aria-hidden="true" class="ks-entity-link__icon" />
        <span class="ks-entity-link__value">{{ value }}</span>
    </RouterLink>
</template>

<script lang="ts">
    export type KsEntityLinkEntity = "namespace" | "flow"
</script>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {RouterLink, type RouteLocationRaw} from "vue-router"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"

    const ENTITY_ICONS: Record<KsEntityLinkEntity, Component> = {
        namespace: FolderOpenOutline,
        flow: FileTreeOutline,
    }

    const props = defineProps<{
        entity: KsEntityLinkEntity
        value: string
        to: RouteLocationRaw
    }>()

    const icon = computed(() => ENTITY_ICONS[props.entity])
</script>

<style scoped lang="scss">
.ks-entity-link {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    max-width: 100%;
    color: var(--ks-text-primary);
    background: var(--ks-bg-tag);
    text-decoration: none;
    border-radius: var(--ks-radius-base);
    padding: 0.125rem var(--ks-spacing-2);
    transition: background-color var(--ks-duration-fast) var(--ks-ease-standard), color var(--ks-duration-fast) var(--ks-ease-standard);
}

.ks-entity-link:hover {
    background: var(--ks-bg-tag-hover);
    color: var(--ks-text-link);
    text-decoration: underline;
}

.ks-entity-link:active {
    background: var(--ks-bg-tag-active);
}

.ks-entity-link:focus-visible {
    outline: var(--ks-border-width-base) solid var(--ks-border-focus);
    outline-offset: var(--ks-spacing-px);
}

.ks-entity-link__icon {
    flex-shrink: 0;
    width: var(--ks-icon-size-sm);
    height: var(--ks-icon-size-sm);
}

.ks-entity-link__value {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
</style>
