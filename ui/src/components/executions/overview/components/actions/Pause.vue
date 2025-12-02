<template>
    <el-button
        :disabled="!enabled"
        :icon="Pause"
        @click="click"
    >
        {{ t('pause') }}
    </el-button>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="t('pause title', {id: execution.id})" />
        </template>
        <template #footer>
            <el-button :icon="Pause" type="primary" @click="pause()" nativeType="submit">
                {{ t('pause') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import Pause from "vue-material-design-icons/Pause.vue";
    import {useExecutionsStore} from "../../../../../stores/executions";
    import permission from "../../../../../models/permission";
    import action from "../../../../../models/action";
    import {State} from "@kestra-io/ui-libs";
    import {useAuthStore} from "override/stores/auth";
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../../../../utils/toast";

    const props = defineProps({
        execution: {
            type: Object,
            required: true
        }
    });

    const {t} = useI18n();
    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();
    const toast = useToast();

    const isDrawerOpen = ref(false);

    const enabled = computed(() => {
        if (!authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace)) {
            return false;
        }
        return State.isRunning(props.execution.state.current) && !State.isPaused(props.execution.state.current);
    });

    const click = () => {
        isDrawerOpen.value = true;
    };

    const pause = () => {
        toast.confirm(t("pause confirm", {id: props.execution.id}), () => {
            return executionsStore
                .pause({
                    id: props.execution.id
                })
                .then(() => {
                    isDrawerOpen.value = false;
                    toast.success(t("pause done"));
                });
        });
    };
</script>
