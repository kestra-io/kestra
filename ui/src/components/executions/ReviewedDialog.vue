<template>
    <KsDialog
        v-model="visible"
        destroyOnClose
        appendToBody
        data-test="reviewed-dialog"
    >
        <template #header>
            <i18n-t keypath="approval.reviewed.title" tag="span" scope="global">
                <template #id><code>{{ execution.id }}</code></template>
            </i18n-t>
        </template>

        <div v-ks-loading="loading">
            <KsAlert type="info" :closable="false" showIcon data-test="reviewed-explanation">
                {{ $t("approval.reviewed.explanation") }}
            </KsAlert>

            <KsDescriptions v-if="decisionKey" class="reviewed-details" :column="1" border>
                <KsDescriptionsItem :label="$t('approval.reviewed.decision')">
                    <span data-test="reviewed-decision">{{ $t(decisionKey) }}</span>
                </KsDescriptionsItem>
                <KsDescriptionsItem v-if="by" :label="$t('approval.reviewed.by')">
                    <span data-test="reviewed-by">{{ by }}</span>
                </KsDescriptionsItem>
                <KsDescriptionsItem v-if="on" :label="$t('approval.reviewed.on')">
                    <span data-test="reviewed-on">{{ on }}</span>
                </KsDescriptionsItem>
                <KsDescriptionsItem v-if="comment" :label="$t('approval.comment')">
                    <span data-test="reviewed-comment">{{ comment }}</span>
                </KsDescriptionsItem>
                <KsDescriptionsItem v-if="reviewInputs" :label="$t('inputs')">
                    <KsJsonTree :value="reviewInputs" defaultExpanded data-test="reviewed-inputs" />
                </KsDescriptionsItem>
            </KsDescriptions>
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {dateUtils, vKsLoading} from "@kestra-io/design-system"
    import {loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"
    import {handledIf} from "../../utils/kestraHttp"
    import type {ApprovalTaskRun} from "../../utils/approval"
    import type {Execution} from "../../stores/executions"

    const props = defineProps<{
        execution: Execution;
        taskRun: ApprovalTaskRun;
    }>()

    const visible = defineModel<boolean>({default: false})

    const DECISION_KEYS: Record<string, string> = {
        APPROVED: "approval.reviewed.decisions.approved",
        DENIED: "approval.reviewed.decisions.denied",
        EXPIRED: "approval.reviewed.decisions.expired",
    }

    const loading = ref(false)
    const outputs = ref<Record<string, unknown>>({})

    const decisionKey = computed(() => DECISION_KEYS[String(outputs.value.decision)])
    const by = computed(() => typeof outputs.value.by === "string" ? outputs.value.by : undefined)
    const on = computed(() => typeof outputs.value.on === "string" ? dateUtils.dateFilter(outputs.value.on) : undefined)
    const comment = computed(() => typeof outputs.value.comment === "string" ? outputs.value.comment : undefined)
    const reviewInputs = computed(() => {
        const value = outputs.value.inputs
        return value && typeof value === "object" && Object.keys(value).length > 0 ? value : undefined
    })

    watch(visible, async (open) => {
        if (!open) {
            return
        }
        loading.value = true
        try {
            outputs.value = await loadTaskRunOutputs(props.execution.id, props.taskRun.id).catch((e: unknown) => {
                if (handledIf(e, [403])) return {}
                throw e
            })
        } finally {
            loading.value = false
        }
    }, {immediate: true})
</script>

<style scoped>
    .reviewed-details {
        margin-top: var(--ks-spacing-4);
    }
</style>
