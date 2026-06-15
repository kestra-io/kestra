<template>
    <KsPopover
        v-model:visible="isOpen"
        :disabled="!enabled"
        trigger="click"
        placement="bottom-start"
        :width="400"
        :showArrow="false"
        :popperStyle="{padding: '0', overflow: 'hidden', borderRadius: '0.875rem', background: 'var(--ks-bg-elevated)', boxShadow: '0px 8px 24px 0px var(--ks-shadow-elevated)'}"
    >
        <template #reference>
            <button class="set-labels-tag" :class="{'is-active': isOpen}" :disabled="!enabled">
                <Plus />
                {{ $t("set_extra_labels") }}
            </button>
        </template>

        <div class="set-labels">
            <div class="set-labels__header">
                <span class="set-labels__title">{{ $t("Set labels") }}</span>
                <KsIconButton :tooltip="$t('close')" placement="top" @click="isOpen = false">
                    <Close />
                </KsIconButton>
            </div>

            <div class="set-labels__body">
                <LabelInput
                    v-model:labels="executionLabels"
                    :existingLabels="executionLabels"
                />
            </div>

            <div class="set-labels__footer">
                <p class="set-labels__description" v-html="$t('Set labels to execution', {id: execution.id})" />
                <div class="set-labels__actions">
                    <KsButton @click="onCancel">
                        {{ $t("cancel") }}
                    </KsButton>
                    <KsButton type="primary" :loading="isSaving" @click="setLabels()">
                        {{ $t("ok") }}
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
    import Plus from "vue-material-design-icons/Plus.vue"
    import {State} from "@kestra-io/design-system"

    import LabelInput from "../labels/LabelInput.vue"
    import {filterValidLabels} from "./utils"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {useExecutionsStore} from "../../stores/executions"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"

    interface Label {
        key: string;
        value: string;
    }

    interface Props {
        execution: {
            id: string;
            namespace: string;
            state: {
                current: string;
            };
            labels?: Label[];
        };
    }

    const props = defineProps<Props>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const miscStore = useMiscStore()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const isOpen = ref(false)
    const executionLabels = ref<Label[]>([])
    const isSaving = ref(false)

    const enabled = computed(() =>
        !!authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace) &&
        !State.isRunning(props.execution.state.current),
    )

    const onCancel = () => {
        isOpen.value = false
        executionLabels.value = []
    }

    const setLabels = async () => {
        const filtered = filterValidLabels(executionLabels.value)

        if (filtered.error) {
            toast.error(t("wrong labels"), t("error"))
            return
        }

        isSaving.value = true
        try {
            const response = await executionsStore.setLabels({
                labels: filtered.labels,
                executionId: props.execution.id,
            })

            if (response?.data) {
                executionsStore.execution = response.data
            }

            toast.success(t("Set labels done"))

            isOpen.value = false
            executionLabels.value = []
        } catch (err) {
            console.error(err)
        } finally {
            isSaving.value = false
        }
    }

    watch(isOpen, (open) => {
        if (!open) {
            executionLabels.value = []
            return
        }

        const toIgnore = miscStore.configs?.hiddenLabelsPrefixes ?? []
        const source = props.execution.labels ?? []

        executionLabels.value = JSON.parse(JSON.stringify(source))
            .filter((label: Label) => !toIgnore.some((prefix: string) => label.key?.startsWith(prefix)))
    })
</script>

<style scoped lang="scss">
    .set-labels-tag {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        background: var(--ks-bg-tag);
        padding: 0.125rem 0.375rem;
        border-radius: var(--ks-radius-sm);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        border: 1px solid transparent;
        cursor: pointer;
        white-space: nowrap;
        font-family: inherit;

        &:hover:not(:disabled) {
            background: var(--ks-bg-hover);
        }

        &.is-active {
            background: var(--ks-btn-secondary-bg-active);
            border-color: var(--ks-btn-secondary-border-active);
        }

        &:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
    }

    .set-labels {
        display: flex;
        flex-direction: column;

        &__header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: var(--ks-spacing-5) var(--ks-spacing-4);
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
            padding: var(--ks-spacing-4);
        }

        &__description {
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
            margin: 0;
            min-width: 0;
        }

        &__footer {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-3) var(--ks-spacing-4);
            border-top: 1px solid var(--ks-border-default);
            background: var(--ks-bg-base);
        }

        &__actions {
            display: flex;
            align-items: center;
            flex-shrink: 0;
        }
    }
</style>
