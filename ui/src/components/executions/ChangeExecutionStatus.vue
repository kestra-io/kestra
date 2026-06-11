<template>
    <KsPopover
        v-model:visible="visible"
        :disabled="!enabled"
        trigger="click"
        placement="bottom-start"
        :width="376"
        :showArrow="false"
        popperClass="change-state-popover"
        :popperStyle="POPPER_STYLE"
    >
        <template #reference>
            <slot name="trigger" :visible="visible" :enabled="enabled">
                <KsButton :disabled="!enabled" :icon="SwapHorizontal">
                    {{ $t('change state') }}
                </KsButton>
            </slot>
        </template>

        <div class="change-state">
            <div class="change-state__header">
                <span class="change-state__title">{{ $t("change state") }}</span>
                <KsIconButton :tooltip="$t('close')" placement="top" @click="visible = false">
                    <Close />
                </KsIconButton>
            </div>

            <div class="change-state__body">
                <div class="change-state__row">
                    <span class="change-state__label">{{ $t("actual state") }}</span>
                    <KsExecutionStatus :status="execution.state.current" />
                </div>

                <div class="change-state__row">
                    <span class="change-state__label">{{ $t("select a state") }}</span>
                    <KsSelect
                        v-model="selectedStatus"
                        :required="true"
                        :teleported="false"
                        class="change-state__select"
                    >
                        <template #label="{value}">
                            <KsExecutionStatus size="small" :status="value" tabindex="-1" />
                        </template>
                        <KsOption
                            v-for="state in states"
                            :key="state"
                            :value="state"
                            :label="state"
                        >
                            <KsExecutionStatus size="small" :status="state" tabindex="-1" />
                        </KsOption>
                    </KsSelect>
                </div>
            </div>

            <div class="change-state__footer">
                <span class="change-state__question">{{ $t("are you sure change state") }}</span>
                <div class="change-state__actions">
                    <KsButton @click="visible = false">
                        {{ $t('cancel') }}
                    </KsButton>
                    <KsButton
                        type="primary"
                        @click="changeStatus()"
                        :disabled="!selectedStatus || selectedStatus === execution.state.current"
                    >
                        {{ $t('yes') }}
                    </KsButton>
                </div>
            </div>
        </div>
    </KsPopover>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import Close from "vue-material-design-icons/Close.vue"
    import SwapHorizontal from "vue-material-design-icons/SwapHorizontal.vue"
    import {State} from "@kestra-io/design-system"

    import action from "../../models/action"
    import resource from "../../models/resource"
    import {Execution, useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"

    const props = defineProps<{ execution: Execution }>()

    const emit = defineEmits<{
        follow: [];
    }>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const PAUSED_STATES = [State.FAILED, State.RUNNING, State.CANCELLED]
    const DEFAULT_STATES = [State.FAILED, State.SUCCESS, State.WARNING, State.CANCELLED]

    const POPPER_STYLE = {
        padding: "0",
        borderRadius: "0.875rem",
        background: "var(--ks-bg-elevated)",
        boxShadow: "0px 8px 24px 0px var(--ks-shadow-elevated)",
    }

    const selectedStatus = ref<string>()
    const visible = ref(false)

    const states = computed(() => {
        const current = props.execution.state.current
        const available = current === "PAUSED" ? PAUSED_STATES : DEFAULT_STATES

        return available.filter(state => state !== current)
    })

    const enabled = computed(() =>
        !!authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace) &&
        !State.isRunning(props.execution.state.current),
    )

    watch(visible, (open) => {
        if (open) selectedStatus.value = states.value[0]
    })

    const changeStatus = async () => {
        visible.value = false

        await executionsStore.changeExecutionStatus({
            executionId: props.execution.id,
            state: selectedStatus.value!,
        })

        const execution = await executionsStore.waitForStateChange(props.execution) as Execution

        executionsStore.execution = execution
        emit("follow")
        toast.success(t("change execution state done"))
    }
</script>

<style lang="scss" scoped>
    .change-state {
        display: flex;
        flex-direction: column;

        &__header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-5) var(--ks-spacing-4);
            padding-bottom: var(--ks-spacing-4);
        }

        &__title {
            font-weight: 600;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-lg);
        }

        &__body {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-3);
            padding: var(--ks-spacing-3) var(--ks-spacing-6);
        }

        &__row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
        }

        &__label {
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        &__select {
            width: 120px;
            flex-shrink: 0;

            :deep(.kel-select__wrapper) {
                padding: 4px 8px 5px 4px;
            }
        }

        &__footer {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-3) var(--ks-spacing-4);
            border-top: 1px solid var(--ks-border-default);
            border-bottom-left-radius: 0.875rem;
            border-bottom-right-radius: 0.875rem;
            background: var(--ks-bg-base);
        }

        &__question {
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
        }

        &__actions {
            display: flex;
            justify-content: flex-end;
            flex-shrink: 0;
        }
    }
</style>
