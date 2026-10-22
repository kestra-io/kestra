<template>
    <NavBarAction
        v-if="enabled"
        :icon="QueueFirstInLastOut"
        @click="isDrawerOpen = !isDrawerOpen"
    >
        {{ $t('unqueue') }}
    </NavBarAction>

    <KsDialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span>{{ $t('unqueue') }}</span>
        </template>

        <template #default>
            <i18n-t keypath="unqueue title" tag="p">
                <template #id><code>{{ execution.id }}</code></template>
            </i18n-t>
            <p>{{ $t("unqueue note") }}</p>

            <KsSelect
                :required="true"
                v-model="selectedStatus"
            >
                <KsOption
                    v-for="state in states"
                    :key="state"
                    :value="state"
                >
                    <template #default>
                        <KsExecutionStatus size="small" :label="true" class="unqueue-status" :status="state" />
                        <i18n-t keypath="unqueue as" tag="span">
                            <template #status><code>{{ state }}</code></template>
                        </i18n-t>
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
    import {computed, ref} from "vue"
    import {useExecutionsStore, type Execution} from "../../../../../stores/executions"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    import {State} from "@kestra-io/design-system"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import {useAuthStore} from "override/stores/auth"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../../../../utils/toast"
    import QueueFirstInLastOut from "vue-material-design-icons/QueueFirstInLastOut.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"

    const props = defineProps<{
        execution: Execution;
    }>()

    const {t} = useI18n()
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const isDrawerOpen = ref(false)
    const selectedStatus = ref(State.RUNNING)

    const states = [State.RUNNING, State.CANCELLED, State.FAILED]

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UNQUEUE, props.execution.namespace))) {
            return false
        }

        return State.isQueued(props.execution.state.current)
    })

    const unqueue = () => {
        executionsStore
            .unqueue({
                id: props.execution.id,
                state: selectedStatus.value,
            })
            .then(() => {
                isDrawerOpen.value = false
                toast.success(t("unqueue done"))
            })
    }
</script>

<style scoped>
.unqueue-status {
    margin-inline-end: var(--ks-spacing-1);
}
</style>
