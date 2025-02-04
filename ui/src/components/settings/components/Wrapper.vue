<template>
    <el-row class="my-5 settings-wrapper">
        <el-col
            :xs="layout(24, 0)"
            :sm="layout(20, 2)"
            :md="layout(18, 3)"
            :lg="layout(16, 4)"
            :xl="layout(14, 5)"
        >
            <slot />
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    const layout = (span, offset) => {
        return {span, offset};
    };
</script>
