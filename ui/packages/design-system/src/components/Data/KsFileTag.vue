<template>
    <KsTooltip :content="tooltip">
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
