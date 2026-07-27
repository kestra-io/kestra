<template>
    <KsDialog v-model="open" destroyOnClose :appendToBody="true" width="560px">
        <template #header>
            <span>{{ t("test_event.title") }}</span>
        </template>

        <p class="test-event-description">
            {{ t("test_event.description", {trigger: target?.triggerId}) }}
        </p>

        <p class="test-event-url">
            {{ url }}
        </p>

        <!-- Plain labels rather than KsFormItem: form items put the label beside the control, and
             an editor reads better underneath its label. -->
        <div class="test-event-field">
            <label class="test-event-label" for="test-event-payload">
                {{ t("test_event.payload") }}
            </label>
            <div id="test-event-payload" class="test-event-editor">
                <KsEditor
                    v-bind="editorBindings"
                    v-model="payload"
                    :options="{fullHeight: false, showScroll: true}"
                    :inline="true"
                    :navbar="false"
                    lang="json"
                />
            </div>
        </div>

        <div class="test-event-field">
            <label class="test-event-label" for="test-event-headers">
                {{ t("test_event.headers") }}
            </label>
            <KsInput
                id="test-event-headers"
                v-model="headers"
                type="textarea"
                :rows="2"
                placeholder="X-Source: my-shop"
            />
        </div>

        <KsAlert v-if="result && result.ok" type="success" :closable="false">
            <template #title>
                <span>{{ t("test_event.response", {status: result.status}) }}</span>
                <RouterLink v-if="result.executionId" :to="executionRoute(result.executionId)" class="test-event-link">
                    {{ t("test_event.execution_created") }}
                </RouterLink>
            </template>
        </KsAlert>

        <KsAlert v-else-if="result" type="error" :closable="false">
            <template #title>
                <span>{{ t("test_event.failed", {status: result.status}) }}</span>
            </template>
        </KsAlert>

        <template #footer>
            <KsButton @click="open = false">
                {{ t("close") }}
            </KsButton>
            <KsButton type="primary" :disabled="sending" @click="send">
                {{ sending ? t("test_event.sending") : t("test_event.send") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"

    import {EXECUTION_PARENT_ROUTE} from "../executions/executionTabs"
    import {KsEditor} from "@kestra-io/design-system"

    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {webhookUrl} from "../../utils/webhook"
    import {parseHeaderLines, sendWebhookTestEvent, type TestEventResult} from "./testEvent"

    export interface TestEventTarget {
        namespace: string;
        flowId: string;
        triggerId: string;
        key: string;
    }

    const props = defineProps<{
        modelValue: boolean;
        target: TestEventTarget | null;
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: boolean];
        sent: [result: TestEventResult];
    }>()

    /** What the payload field starts with, so an event can be sent without writing JSON first. */
    const SAMPLE_PAYLOAD = `{
  "order_id": 1042,
  "customer": "ACME",
  "total": 187.5
}`

    const {t} = useI18n()
    const route = useRoute()
    const editorBindings = useEditorBindings()

    const isOpen = computed(() => props.modelValue)
    const target = computed(() => props.target)

    const payload = ref(SAMPLE_PAYLOAD)
    const headers = ref("")
    const sending = ref(false)
    const result = ref<TestEventResult | null>(null)

    const open = computed({
        get: () => props.modelValue,
        set: (value: boolean) => emit("update:modelValue", value),
    })

    const url = computed(() =>
        target.value
            ? webhookUrl({namespace: target.value.namespace, id: target.value.flowId, key: target.value.key})
            : "",
    )

    const executionRoute = (id: string) => ({
        name: `${EXECUTION_PARENT_ROUTE}/gantt`,
        params: {
            namespace: target.value?.namespace,
            flowId: target.value?.flowId,
            id,
            tenant: route.params.tenant,
        },
    })

    // A fresh payload every time the dialog opens, so a previous edit is not carried over.
    watch(isOpen, (value) => {
        if (value) {
            payload.value = SAMPLE_PAYLOAD
            result.value = null
        }
    })

    const send = async () => {
        if (!target.value) {
            return
        }
        sending.value = true
        try {
            const sent = await sendWebhookTestEvent({
                namespace: target.value.namespace,
                flowId: target.value.flowId,
                key: target.value.key,
                payload: payload.value,
                headers: parseHeaderLines(headers.value),
            })
            result.value = sent
            emit("sent", sent)
        } finally {
            sending.value = false
        }
    }
</script>

<style scoped lang="scss">
    .test-event-description {
        margin-bottom: 0.5rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .test-event-url {
        margin-bottom: 1rem;
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-input, var(--ks-bg-base));
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-monospace, monospace);
        font-size: var(--ks-font-size-xs);
        word-break: break-all;
    }

    .test-event-field {
        margin-bottom: 1rem;
    }

    .test-event-label {
        display: block;
        margin-bottom: 0.375rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
    }

    .test-event-editor {
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        overflow: hidden;
    }

    .test-event-link {
        margin-left: 0.5rem;
    }
</style>
