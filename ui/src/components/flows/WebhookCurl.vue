<template>
    <div class="webhook-curl">
        <div v-if="webhookTriggers.length > 0">
            <el-form-item :label="$t('webhook.payload')" class="payload">
                <Editor
                    :fullHeight="false"
                    :input="true"
                    :navbar="false"
                    lang="json"
                    :showScroll="true"
                    v-model="webhookPayload"
                />
            </el-form-item>
            <div v-for="trigger in webhookTriggers" :key="trigger.id" class="trigger">
                <div class="code">
                    <pre><code>{{ generateWebhookCurlCommand(trigger) }}</code></pre>
                    <CopyToClipboard :text="generateWebhookCurlCommand(trigger)" class="copy" />
                </div>
            </div>

            <el-alert type="info" showIcon :closable="false">
                {{ $t('webhook.curl_note') }}
            </el-alert>
        </div>
        <div v-else>
            <el-alert type="warning" showIcon :closable="false">
                {{ $t('webhook.no_triggers') }}
            </el-alert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";
    import CopyToClipboard from "../layout/CopyToClipboard.vue";
    import Editor from "../inputs/Editor.vue";
    import {baseUrl, basePath, apiUrl} from "../../override/utils/route";
    import {useFlowStore} from "../../stores/flow";

    interface Flow {
        namespace: string;
        id: string;
        triggers?: Trigger[];
    }

    interface Trigger {
        id: string;
        type: string;
        key?: string;
        disabled?: boolean;
    }

    const props = defineProps<{
        flow: Flow;
    }>();

    const webhookPayload = ref("{\"key1\":\"value1\",\"key2\":\"value2\"}");

    const flowStore = useFlowStore();
    const webhookTriggers = computed(() => {
        const sourceFlow = flowStore.flow || props.flow;

        if (!sourceFlow?.triggers) {
            return [];
        }

        return sourceFlow.triggers.filter((trigger: Trigger) =>
            trigger.type === "io.kestra.plugin.core.trigger.Webhook" &&
            (trigger.disabled === undefined || trigger.disabled === false)
        );
    });

    const generateWebhookUrl = (trigger: Trigger): string => {
        const origin = baseUrl ? apiUrl() : `${location.origin}${basePath()}`;
        return `${origin}/executions/webhook/${props.flow.namespace}/${props.flow.id}/${trigger.key}`;
    };

    const generateWebhookCurlCommand = (trigger: Trigger): string => {
        if (!trigger.key) {
            return "Webhook key not available";
        }

        const command = [`curl -X POST ${generateWebhookUrl(trigger)}`];
        command.push("-H \"Content-Type: application/json\"");

        if (webhookPayload.value.trim()) {
            command.push(`-d '${webhookPayload.value}'`);
        }

        return toShell(command);
    };

    const toShell = (command: string[]): string => {
        return command.join(" \\\n  ");
    };

    onMounted(async () => {
        if (props.flow?.namespace && props.flow?.id) {
            try {
                await flowStore.loadFlow({
                    namespace: props.flow.namespace,
                    id: props.flow.id
                });
            } catch (error) {
                throw new Error(`Failed to load flow: ${error}`);
            }
        }
    });
</script>

<style scoped lang="scss">
.webhook-curl {
    position: relative;
    border: 1px solid var(--ks-border-primary);
    padding: 1rem;
    border-radius: 0.5rem;

    .payload {
        margin-bottom: 1rem;

        :deep(.el-form-item__label) {
            font-size: var(--font-size-sm);
            color: var(--ks-content-secondary);
        }

        :deep(.editor-container) {
            height: 50px;
            max-height: 120px;
        }
    }

    .code {
        position: relative;
        pre {
            overflow-x: auto;
        }
    }

    .copy {
        position: absolute;
        top: 8px;
        right: 8px;
        z-index: 10;
    }
}
</style>
