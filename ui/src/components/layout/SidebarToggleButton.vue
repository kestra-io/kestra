<template>
    <el-button
        class="collapseButton sidebar-toggle"
        @click="$emit('toggle')"
    >
        <svg 
            width="12" 
            height="12" 
            viewBox="0 0 12 12" 
            fill="none" 
            xmlns="http://www.w3.org/2000/svg"
        >
            <path 
                fill-rule="evenodd"
                clip-rule="evenodd"
                d="M11.8554 10.9932C11.8567 11.4542 11.4841 11.8289 11.0231 11.8301L1.02524 11.858C0.564312 11.8593 0.189613 11.4867 0.188327 11.0258L0.160404 1.01728C0.159118 0.556349 0.531732 0.181649 0.99266 0.180363L10.9906 0.152469C11.4515 0.151183 11.8262 0.523797 11.8275 0.984726L11.8554 10.9932ZM11.0318 11.0054L5.18316 11.0217L5.15511 0.967535L11.0037 0.951218L11.0318 11.0054ZM4.31027 11.023L0.975876 11.0323L0.947825 0.978203L4.28221 0.9689L4.31027 11.023Z" 
                fill="currentColor" 
            />
        </svg>
    </el-button>
</template>

<script setup lang="ts">
    defineEmits<{
        (e: "toggle"): void;
    }>();
</script>


<style scoped lang="scss">
    .sidebar-toggle {
        border: none;
        color: var(--ks-text-secondary);

        &:hover {
            color: var(--ks-content-link);
        }

        html.dark & {
            color: var(--ks-text-secondary);
        }
    }
</style>
