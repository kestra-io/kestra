<template>
    <KsTooltip :content="tooltip" popperClass="ks-file-tag-tooltip">
        <KsTag :icon="icon" :label="label" truncate />
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import KsTag from "./KsTag/KsTag.vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {fileExtension, fileIcon, fileName} from "../../utils/file"

    const props = defineProps<{
        /** Storage URI of the file, always shown in full in the tooltip. */
        uri: string
        /** Label to display; defaults to the URI's last path segment. */
        name?: string
    }>()

    const label = computed(() => props.name || fileName(props.uri) || props.uri)

    // Generated storage URIs keep the extension the caller-supplied name often lacks.
    const icon = computed(() => fileIcon(fileExtension(props.uri) ? props.uri : label.value))

    // A caller-supplied name is the part that gets clipped, so the tooltip has to carry it too.
    const tooltip = computed(() => (props.name ? `${label.value} (${props.uri})` : props.uri))
</script>

<style lang="scss">
    // Teleported popper — keep unscoped so the class on body can wrap long storage URIs.
    .kel-popper.ks-tooltip.ks-file-tag-tooltip {
        max-width: min(20rem, 90vw);
        overflow-wrap: anywhere;
    }
</style>
