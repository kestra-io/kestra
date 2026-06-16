<template>
    <KsButton
        v-if="enabled"
        :icon="Play"
        @click="click"
    >
        {{ $t('resume') }}
    </KsButton>

    <KsDialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="$t('resume from breakpoint title', {id: execution.id})" />
        </template>
        <KsForm labelPosition="top" ref="form" @submit.prevent="false">
            <KsFormItem :label="$t('breakpoints')">
                <KsSelect
                    v-model="selectedBreakpoints"
                    multiple
                    filterable
                    :placeholder="$t('breakpoints')"
                    class="breakpoints-select"
                >
                    <KsOption
                        v-for="taskId in taskIds"
                        :key="taskId"
                        :label="taskId"
                        :value="taskId"
                    />
                </KsSelect>
            </KsFormItem>
        </KsForm>
        <template #footer>
            <KsButton :icon="Play" type="primary" @click="resume()" nativeType="submit">
                {{ $t('resume') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, onMounted} from "vue"
    import Play from "vue-material-design-icons/Play.vue"
    import {useExecutionsStore} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    import {getAllTaskIds} from "../../../../../utils/flowUtils"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../../../../utils/toast"

    const props = defineProps({
        execution: {
            type: Object,
            required: true,
        },
    })

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const toast = useToast()

    const isDrawerOpen = ref(false)
    const selectedBreakpoints = ref<string[]>([])

    const enabled = computed(() => {
        if (!authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace)) {
            return false
        }
        return props.execution.state.current === "BREAKPOINT"
    })

    const taskIds = computed(() => {
        return getAllTaskIds(executionsStore.flow)
    })

    const click = () => {
        isDrawerOpen.value = true
    }

    const resume = () => {
        executionsStore
            .resumeFromBreakpoint({
                id: props.execution.id,
                breakpoints: selectedBreakpoints.value,
            })
            .then(() => {
                isDrawerOpen.value = false
                toast.success(t("resume from breakpoint done"))
            })
    }

    onMounted(() => {
        if (enabled.value) {
            executionsStore.loadFlowForExecution({
                flowId: props.execution.flowId,
                namespace: props.execution.namespace,
                store: true,
            })
            if (props.execution.breakpoints) {
                selectedBreakpoints.value = props.execution.breakpoints.map((b: any) => b.value ? `${b.id}.${b.value}` : b.id)
            }
        }
    })
</script>

<style scoped>
.breakpoints-select {
    width: 100%;
}
</style>
