<template>
    <div class="wrapper">
        <div class="icon" :style="styles" />
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue";

    interface Props {
        encodedSvg?: string;
    }

    const props = defineProps<Props>();

    const styles = computed(() => ({
        backgroundImage: props.encodedSvg
            ? `url(data:image/svg+xml;base64,${props.encodedSvg})`
            : "none",
    }));
</script>

<style scoped lang="scss">
    .wrapper {
        display: inline-block;
        width: 100%;
        height: 100%;
        position: relative;

        :deep(span) {
            position: absolute;
            padding: 1px;
            left: 0;
            display: block;
            width: 100%;
            height: 100%;
        }

        :deep(.icon) {
            width: 100%;
            height: 100%;
            display: block;
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center center;
        }
    }
</style>