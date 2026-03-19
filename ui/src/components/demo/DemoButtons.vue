<template>
    <div>
        <a class="el-button el-button--large el-button--primary" target="_blank" :href="`https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-${type}`">
            {{ $t("demos.get_a_demo_button") }}
        </a>
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        type: string;
    }>();
</script>