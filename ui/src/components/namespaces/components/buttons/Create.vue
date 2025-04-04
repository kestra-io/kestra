<template>
    <ul>
        <li>
            <el-button :icon="Plus" type="primary" @click="emits('click')">
                {{ props.label }}
            </el-button>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import Plus from "vue-material-design-icons/Plus.vue";

    const props = defineProps<{ label: string }>();
    const emits = defineEmits<{ (event: "click"): void }>();
</script>
