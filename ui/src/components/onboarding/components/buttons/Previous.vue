<template>
    <el-button :icon="ChevronLeft" class="alternative">
        {{ $t("onboarding.previous") }}
    </el-button>
</template>

<script setup lang="ts">
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
</script>
