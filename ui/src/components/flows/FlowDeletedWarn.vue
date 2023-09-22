<script setup>
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue"
</script>
<script>
    import Alert from "vue-material-design-icons/Alert.vue"

    export default {
        name: "FlowDeletedWarn",
        emits: ["restoreFlow"],
        components: {
            Alert
        },
        methods: {
            restoreFlow() {
                this.$emit("restoreFlow");
            }
        }
    }
</script>

<template>
    <el-card class="mb-2" body-class="p-2">
        <div class="d-flex justify-content-between align-items-center">
            <span class="warning-text d-flex align-items-center">
                <Alert class="warning-icon mx-3" />
                {{ $t("flow deleted, you can restore it") }}
            </span>

            <span>
                <el-button :icon="BackupRestore" class="warning-button" @click="restoreFlow()">
                    {{ $t("restore") }}
                </el-button>
            </span>
        </div>
    </el-card>
</template>

<style scoped lang="scss">
    .warning-icon {
        color: var(--bs-warning);
        font-size: 2rem;
    }
    .warning-text {
        .html.dark & {
            color: white;
        }
    }
    .warning-button {
        background-color: var(--bs-body-bg);
    }
</style>