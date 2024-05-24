<template>
    <el-button :icon="ChevronLeft" class="previous">
        {{ $t("onboarding.previous") }}
    </el-button>
</template>

<script setup lang="ts">
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
</script>

<style scoped lang="scss">
$background: #2f3342;
$color: #cac5da;

.previous {
    background: $background;

    html.light & {
        color: $color;

        &:hover {
            color: $background;
        }
    }
}
</style>
