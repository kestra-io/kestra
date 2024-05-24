<template>
    <el-button class="skip">
        {{ $t("onboarding.skip") }}
    </el-button>
</template>

<style scoped lang="scss">
$background: #2f3342;
$color: #cac5da;

.skip {
    background: $background;

    html.light & {
        color: $color;

        &:hover {
            color: $background;
        }
    }
}
</style>
