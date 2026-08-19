<template>
    <KsDialog v-model="open" destroyOnClose :appendToBody="true" width="560px" scrollable>
        <template #header>
            <span>{{ $t("test_event.title") }}</span>
        </template>

        <p class="test-event-description">
            {{ $t("test_event.description", {trigger: target?.triggerId}) }}
        </p>

        <p class="test-event-url">
            {{ url }}
        </p>

        <KsForm labelPosition="top">
            <KsFormItem :label="$t('test_event.payload')">
                <KsEditor
                    v-bind="editorBindings"
                    v-model="payload"
                    :options="{fullHeight: false, showScroll: true}"
                    :inline="true"
                    :navbar="false"
                    lang="json"
                />
            </KsFormItem>

            <KsFormItem :label="$t('test_event.headers')">
                <KsInput
                    v-model="headers"
                    type="textarea"
                    :rows="2"
                    placeholder="X-Source: my-shop"
                />
            </KsFormItem>
        </KsForm>

        <KsAlert v-if="result && result.ok" type="success" :closable="false">
            <template #title>
                <span>{{ $t("test_event.response", {status: result.status}) }}</span>
                <RouterLink v-if="result.executionId" :to="executionRoute(result.executionId)" class="test-event-link">
                    {{ $t("test_event.execution_created") }}
                </RouterLink>
            </template>
        </KsAlert>

        <KsAlert v-else-if="result" type="error" :closable="false">
            <template #title>
                <span>{{ $t("test_event.failed", {status: result.status}) }}</span>
            </template>
        </KsAlert>

        <template #footer>
            <KsButton @click="open = false">
                {{ $t("close") }}
            </KsButton>
            <KsButton type="primary" :disabled="sending" @click="send">
                {{ sending ? $t("test_event.sending") : $t("test_event.send") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"

    import {EXECUTION_PARENT_ROUTE} from "../executions/executionTabs"
    import {KsEditor} from "@kestra-io/design-system"

    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {webhookUrl} from "../../utils/webhook"
    import {parseHeaderLines, sendWebhookTestEvent, SAMPLE_TEST_EVENT_PAYLOAD, type TestEventResult} from "./testEvent"

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

    const route = useRoute()
    const editorBindings = useEditorBindings()

    const target = computed(() => props.target)

    const payload = ref(SAMPLE_TEST_EVENT_PAYLOAD)
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

    watch(open, (value) => {
        if (value) {
            payload.value = SAMPLE_TEST_EVENT_PAYLOAD
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
        margin-bottom: var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .test-event-url {
        margin-bottom: var(--ks-spacing-4);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-input);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        word-break: break-all;
    }


    .test-event-link {
        margin-left: var(--ks-spacing-2);
    }
</style>
