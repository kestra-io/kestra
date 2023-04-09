<template>
    <el-tooltip :disabled="!error" raw-content transition="" :hide-after="0" :persistent="false">
        <template #content>
            <pre>{{ error }}</pre>
        </template>
        <el-button :link="link" type="default">
            <component :class="'text-' + (error ? 'danger' : 'success')" :is="error ? CloseCircleOutline : CheckCircleOutline" />
        </el-button>
    </el-tooltip>
</template>

<script setup>
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue";
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue";
</script>

<script>
    export default {
        props: {
            error: {
                type: String,
                default: undefined
            },
            link: {
                type: Boolean,
                default: false
            },
        },

    };
</script>


<style lang="scss" scoped>
    pre {
        margin-bottom: 0;
        max-width: 40vw;
        white-space: pre-wrap;
        background: transparent;
        padding: 0;
    }

    .el-button.el-button--default {
        background-color: var(--bs-body-bg);
    }
</style>