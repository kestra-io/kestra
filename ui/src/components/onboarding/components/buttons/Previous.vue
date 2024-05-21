<template>
    <el-button :icon="ChevronLeft">
        {{ $t("onboarding.previous") }}
    </el-button>
</template>

<script setup lang="ts">
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
</script>
