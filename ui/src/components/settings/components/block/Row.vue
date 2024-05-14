<template>
    <el-row :gutter="24" class="row">
        <slot />
    </el-row>
</template>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables.scss";

.row {
    margin-bottom: $spacer;
}
</style>
