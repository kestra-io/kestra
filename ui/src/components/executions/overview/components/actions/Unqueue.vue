<template>
    <KsButton
        v-if="enabled"
        :icon="QueueFirstInLastOut"
        @click="isDrawerOpen = !isDrawerOpen"
    >
        {{ $t('unqueue') }}
    </KsButton>

    <KsDialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="$t('unqueue')" />
        </template>

        <template #default>
            <p v-html="$t('unqueue title', {id: execution.id})" />

            <KsSelect
                :required="true"
                v-model="selectedStatus"
            >
                <KsOption
                    v-for="item in states"
                    :key="item.code"
                    :value="item.code"
                >
                    <template #default>
                        <KsExecutionStatus size="small" :label="true" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </KsOption>
            </KsSelect>
        </template>

        <template #footer>
            <KsButton :icon="QueueFirstInLastOut" type="primary" @click="unqueue()" nativeType="submit">
                {{ $t('unqueue') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useExecutionsStore} from "../../../../../stores/executions";
    import resource from "../../../../../models/resource";
    import action from "../../../../../models/action";
    import {State} from "@kestra-io/design-system"
    import {KsExecutionStatus} from "@kestra-io/design-system"
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
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
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