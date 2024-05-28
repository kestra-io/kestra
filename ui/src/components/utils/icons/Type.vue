<template>
    <img :src="icon" :alt="name" width="18">
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {getIcon} from "material-file-icons";
    import {ICONS} from "./icons";

    interface Props {
        name: string;
        folder?: boolean;
    }
    const props = defineProps<Props>();

    const icon = computed<string>(() => {
        const SVG = props.folder ? ICONS.FOLDER : getIcon(props.name).name === "file" ? ICONS.KESTRA : getIcon(props.name).svg;
        return `data:image/svg+xml;base64,${btoa(SVG)}`;
    });
</script>
