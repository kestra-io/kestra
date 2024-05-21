<template>
    <el-button>
        {{ $t("onboarding.skip") }}
        <el-icon class="el-icon--right">
            <ChevronRight />
        </el-icon>
    </el-button>
</template>

<script setup lang="ts">
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
</script>
