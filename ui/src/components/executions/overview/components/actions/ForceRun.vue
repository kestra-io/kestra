<template>
    <el-button
        :disabled="!enabled"
        :icon="RunFast"
        @click="click"
    >
        {{ $t("force run") }}
    </el-button>

    <el-dialog
        v-if="isDrawerOpen"
        v-model="isDrawerOpen"
        destroyOnClose
        :appendToBody="true"
    >
        <template #header>
            <span v-html="$t('force run title', {id: execution.id})" />
        </template>
        <template #footer>
            <el-button
                :icon="QueueFirstInLastOut"
                type="primary"
                @click="forceRun()"
                nativeType="submit"
            >
                {{ $t("force run") }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import {State} from "@kestra-io/ui-libs";
    import permission from "../../../../../models/permission";
    import action from "../../../../../models/action";
    import {useExecutionsStore} from "../../../../../stores/executions";
    import {useAuthStore} from "override/stores/auth";

    import {useI18n} from "vue-i18n";
    import {useToast} from "../../../../../utils/toast";

    import RunFast from "vue-material-design-icons/RunFast.vue";
    import QueueFirstInLastOut from "vue-material-design-icons/QueueFirstInLastOut.vue";

    interface ExecutionState {
        current: string;
    }

    interface Execution {
        id: string;
        namespace: string;
        state: ExecutionState;
    }

    const props = defineProps<{
        execution: Execution;
    }>();


    const isDrawerOpen = ref(false);

    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();

    const {t} = useI18n({useScope: "global"});
    const toast = useToast();

    const click = () => {
        toast.confirm(t("force run confirm", {id: props.execution.id}), () => {
            return forceRun();
        });
    };

    const forceRun = async () => {
        try {
            await executionsStore.forceRun({id: props.execution.id});
            isDrawerOpen.value = false;
            toast.success(t("force run done"));
        } catch (err) {
            console.error(err);
        }
    };

    const enabled = computed(() => {
        const user = authStore.user;

        if (
            !user?.isAllowed(
                permission.EXECUTION,
                action.UPDATE,
                props.execution.namespace,
            )
        ) {
            return false;
        }

        return (
            State.isRunning(props.execution.state.current) ||
            State.isQueued(props.execution.state.current)
        );
    });
</script>