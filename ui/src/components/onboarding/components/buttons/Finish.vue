<template>
    <el-button class="finish">
        {{ $t("onboarding.finish") }}
    </el-button>
</template>

<style scoped lang="scss">
$background: #2f3342;
$color: #cac5da;

.finish {
    background: $background;

    html.light & {
        color: $color;

        &:hover {
            color: $background;
        }
    }
}
</style>
