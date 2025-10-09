<template>
    <component
        :is="component"
        :icon="PauseBox"
        @click="click"
        :disabled="!enabled"
        class="ms-0 me-1"
    >
        {{ t('pause') }}
    </component>
</template>

<script setup lang="ts">
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
    import {useExecutionsStore} from "../../stores/executions";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs";
    import {useAuthStore} from "override/stores/auth";
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../utils/toast";

    const props = defineProps({
        execution: {
            type: Object,
            required: true
        },
        component: {
            type: String,
            default: "el-button"
        }
    });

    const {t} = useI18n();
    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();
    const toast = useToast();

    const enabled = computed(() => {
        if (!authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace)) {
            return false;
        }
        return State.isRunning(props.execution.state.current) && !State.isPaused(props.execution.state.current);
    });

    const click = () => {
        toast.confirm(t("pause confirm", {id: props.execution.id}), () => {
            return pause();
        });
    };

    const pause = () => {
        return executionsStore
            .pause({
                id: props.execution.id
            })
            .then(() => {
                toast.success(t("pause done"));
            });
    };
</script>

<style scoped lang="scss">
    button.el-button {
        cursor: pointer !important;
    }
</style>
