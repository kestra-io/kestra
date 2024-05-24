<template>
    <el-button class="next">
        {{ $t("onboarding.next") }}
        <el-icon class="el-icon--right">
            <ChevronRight />
        </el-icon>
    </el-button>
</template>

<script setup lang="ts">
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
</script>

<style scoped lang="scss">
$background: #2f3342;
$color: #cac5da;

.next {
    background: $background;

    html.light & {
        color: $color;

        &:hover {
            color: $background;
        }
    }
}
</style>
