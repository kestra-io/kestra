<template>
    <el-dialog
        :modelValue="removedTriggers.length > 0"
        :title="$t('disabled trigger removed.title')"
        width="500px"
        alignCenter
        :closeOnClickModal="false"
        :showClose="false"
        @close="flowStore.answerRemovedDisabledTriggers(false)"
    >
        <p class="dialog-message" data-test="disabled-trigger-removed-message">
            {{ $t("disabled trigger removed.message", {triggers: removedTriggers.join(", ")}) }}
        </p>
        <template #footer>
            <el-button @click="flowStore.answerRemovedDisabledTriggers(false)">
                {{ $t("cancel") }}
            </el-button>
            <el-button
                type="primary"
                data-test="disabled-trigger-removed-confirm"
                @click="flowStore.answerRemovedDisabledTriggers(true)"
            >
                {{ $t("ok") }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import {useFlowStore} from "../../stores/flow";

    const flowStore = useFlowStore();

    const removedTriggers = computed(() => flowStore.removedDisabledTriggers);
</script>

<style scoped lang="scss">
.dialog-message {
    margin: 0;
    line-height: 1.6;
}
</style>
