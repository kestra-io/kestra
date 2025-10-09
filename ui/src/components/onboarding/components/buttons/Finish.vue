<template>
    <el-button class="alternative">
        {{ $t("onboarding.finish") }}
    </el-button>
</template>
<style scoped lang="scss">
    .alternative {
        border: 1px solid #8405FF;
        animation: glowAnimation 1s infinite alternate;
    }

    @keyframes glowAnimation {
        0% {
            box-shadow: 0px 0px 0px 0px #8405FF;
        }
        100% {
            box-shadow: 0px 0px 50px 2px #8405FF;
        }
    }
</style>