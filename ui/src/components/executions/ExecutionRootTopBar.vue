<template>
    <TopNavBar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #title>
            {{ routeInfo?.title }}
            <Badge v-if="isATestExecution" :label="t('test-badge-text')" :tooltip="t('test-badge-tooltip')" />
        </template>
        <template #actions>
            <slot name="actions" />
            <div class="d-flex align-items-center gap-2" v-if="hasVisibleActions && route.params.tab !== 'audit-logs'">
                <ul class="d-none d-xl-flex align-items-center">
                    <li v-if="isAllowedEdit" data-onboarding-target="execution-edit-flow-button">
                        <KsButton
                            class="execution-edit-flow-button"
                            :icon="Pencil"
                            @click="editFlow"
                        >
                            {{ t("edit flow") }}
                        </KsButton>
                    </li>
                </ul>

                <KsDropdown class="d-flex d-xl-none align-items-center">
                    <KsButton>
                        <KsIcon><DotsVerticalIcon /></KsIcon>
                        <span class="d-none d-lg-inline-block">{{ t("more_actions") }}</span>
                    </KsButton>
                    <template #dropdown>
                        <KsDropdownMenu>
                            <KsDropdownItem v-if="isAllowedEdit" @click="editFlow">
                                <KsIcon><Pencil /></KsIcon>
                                {{ t("edit flow") }}
                            </KsDropdownItem>
                        </KsDropdownMenu>
                    </template>
                </KsDropdown>

                <div v-if="primaryAction || fallbackToExecute">
                    <div class="d-flex align-items-center gap-2">
                        <component
                            v-if="primaryAction"
                            :is="primaryAction.component"
                            v-bind="primaryAction.props"
                            :execution="execution ?? {}"
                            type="primary"
                        />

                        <TriggerFlow
                            v-else-if="fallbackToExecute"
                            type="primary"
                            :flowId="(route.params.flowId as string)"
                            :namespace="(route.params.namespace as string)"
                        />
                    </div>
                </div>
            </div>
        </template>
    </TopNavBar>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import DotsVerticalIcon from "vue-material-design-icons/DotsVertical.vue"
    import {State} from "@kestra-io/design-system"
    import Badge from "../global/Badge.vue"
    import TriggerFlow from "../flows/TriggerFlow.vue"
    import Pause from "./overview/components/actions/Pause.vue"
    import Resume from "./overview/components/actions/Resume.vue"
    import Restart from "./overview/components/actions/Restart.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    const props = defineProps<{
        routeInfo: { title: string; breadcrumb?: any[] } // FIXME: type breadcrumb properly
    }>()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const execution = computed(() => executionsStore.execution)

    const isAllowedEdit = computed(() =>
        execution.value && authStore.user?.isAllowed(resource.FLOW, action.UPDATE, execution.value.namespace),
    )

    const isAllowedTrigger = computed(() =>
        execution.value && authStore.user?.isAllowed(resource.EXECUTION, action.CREATE, execution.value.namespace),
    )

    const primaryAction = computed(() => {
        if (!execution.value?.state) {
            return null
        }

        if (State.isPaused(execution.value.state.current)) {
            return {
                component: Resume,
                props: {},
            }
        }

        if (State.isRunning(execution.value.state.current)) {
            return {
                component: Pause,
                props: {},
            }
        }

        if (execution.value.state.current === State.FAILED) {
            return {
                component: Restart,
                props: {},
            }
        }

        if (State.getTerminatedStates().includes(execution.value.state.current)) {
            return {
                component: Restart,
                props: {
                    isReplay: true,
                },
            }
        }

        return null
    })

    const fallbackToExecute = computed(() =>
        execution.value && isAllowedTrigger.value && !primaryAction.value,
    )

    const hasVisibleActions = computed(() =>
        isAllowedEdit.value || primaryAction.value || fallbackToExecute.value,
    )

    const isATestExecution = computed(() =>
        execution.value &&
        execution.value.labels &&
        execution.value.labels.some((label: { key: string; value: string }) =>
            label.key === "system.test" && label.value === "true",
        ),
    )

    function editFlow() {
        router.push({
            name: "flows/update",
            params: {
                namespace: route.params.namespace,
                id: route.params.flowId,
                tab: "edit",
                tenant: route.params.tenant,
            },
        })
    }
</script>

<style scoped>

@media (max-width: 575.98px) {
  .sm-extra-padding {
    padding: 0;
  }
}

</style>
