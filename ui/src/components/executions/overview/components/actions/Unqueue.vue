<template>
    <el-button
        v-if="enabled"
        :icon="QueueFirstInLastOut"
        @click="isDrawerOpen = !isDrawerOpen"
    >
        {{ $t('unqueue') }}
    </el-button>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="$t('unqueue')" />
        </template>

        <template #default>
            <p v-html="$t('unqueue title', {id: execution.id})" />

            <el-select
                :required="true"
                v-model="selectedStatus"
                :persistent="false"
            >
                <el-option
                    v-for="item in states"
                    :key="item.code"
                    :value="item.code"
                >
                    <template #default>
                        <Status size="small" :label="true" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>

        <template #footer>
            <el-button :icon="QueueFirstInLastOut" type="primary" @click="unqueue()" nativeType="submit">
                {{ $t('unqueue') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useExecutionsStore} from "../../../../../stores/executions";
    import permission from "../../../../../models/permission";
    import action from "../../../../../models/action";
    import {State, Status} from "@kestra-io/ui-libs"
    import {useAuthStore} from "override/stores/auth"
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../../../../utils/toast";
    import QueueFirstInLastOut from "vue-material-design-icons/QueueFirstInLastOut.vue";

    interface Execution {
        id: string;
        namespace: string;
        state: {
            current: string;
        };
    }

    const props = defineProps<{
        execution: Execution;
    }>();

    const {t} = useI18n();
    const toast = useToast();
    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();

    const isDrawerOpen = ref(false);
    const selectedStatus = ref(State.RUNNING);

    const states = computed(() => {
        return [State.RUNNING, State.CANCELLED, State.FAILED].map(value => ({
            code: value,
            label: t("unqueue as", {status: value}),
        }));
    });

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false;
        }

        return State.isQueued(props.execution.state.current);
    });

    const unqueue = () => {
        executionsStore
            .unqueue({
                id: props.execution.id,
                state: selectedStatus.value
            })
            .then(() => {
                isDrawerOpen.value = false;
                toast.success(t("unqueue done"));
            });
    }
</script>