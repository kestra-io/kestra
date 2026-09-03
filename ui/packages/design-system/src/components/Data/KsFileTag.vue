<template>
    <KsTooltip :content="uri">
        <span class="ks-file-tag">
            <component :is="icon" aria-hidden="true" class="ks-file-tag__icon" />
            <span class="ks-file-tag__name">{{ label }}</span>
        </span>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {fileExtension, fileIcon, fileName} from "../../utils/file"

    const props = defineProps<{
        /** Storage URI of the file, shown in full in the tooltip. */
        uri: string
        /** Label to display; defaults to the URI's last path segment. */
        name?: string
    }>()

    const label = computed(() => props.name || fileName(props.uri) || props.uri)

    // Generated storage URIs keep the extension the caller-supplied name often lacks.
    const icon = computed(() => fileIcon(fileExtension(props.uri) ? props.uri : label.value))
</script>

<style scoped lang="scss">
.ks-file-tag {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    max-width: 100%;
    padding: 0.125rem var(--ks-spacing-2);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-tag);
    color: var(--ks-text-primary);
}

.ks-file-tag__icon {
    flex-shrink: 0;
    width: var(--ks-icon-size-sm);
    height: var(--ks-icon-size-sm);
    color: var(--ks-icon-default);
}

.ks-file-tag__name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
</style>
