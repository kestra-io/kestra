<template>
    <div class="state">
        <span class="circle" :style="{backgroundColor: getScheme(theme, label)}" />

        <p class="m-0 fw-light small">
            {{ label.toLowerCase().capitalize() }}
        </p>
    </div>
</template>

<script setup>
    import {useTheme} from "../../../utils/utils.js";
    import {getScheme} from "../../../utils/scheme.js";

    const theme = useTheme();

    defineProps({
        label: {
            type: String,
            required: true,
        },
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

.state {
    display: flex;
    align-items: center;

    & .circle {
        display: inline-block;
        margin-right: 8px;
        width: 5px;
        height: 5px;
        border-radius: 50%;
    }

    & .small {
        font-size: $font-size-xs;
        color: $gray-700;

        html.dark & {
            color: $gray-300;
        }
    }
}
</style>
