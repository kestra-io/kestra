<template>
    <AiCopilotWrapper
        ref="copilotWrapper"
        :flow="editorContent"
        :generationType="aiGenerationTypes.DASHBOARD"
        @generated-yaml="(yaml: string) => { draftSource = yaml }"
    >
        <template #default="{aiCopilotAllowed, aiCopilotOpened, openAiCopilot}">
            <Editor
                v-model="editorContent"
                schemaType="dashboard"
                lang="yaml"
                :navbar="false"
                @cursor="cursor"
                :original="hasDraft ? dashboardStore.sourceCode : undefined"
                :diffOverviewBar="false"
                :diffSideBySide="false"
            >
                <template #absolute>
                    <AITriggerButton
                        v-if="aiCopilotAllowed"
                        :show="true"
                        :opened="aiCopilotOpened"
                        @click="() => { draftSource = undefined; openAiCopilot(); }"
                    />
                </template>
                <template #footer-row>
                    <AcceptDecline :visible="hasDraft" @accept="acceptDraft" @reject="declineDraft" />
                </template>
            </Editor>
        </template>
    </AiCopilotWrapper>
</template>

<script lang="ts" setup>
    import {onMounted, ref, computed} from "vue";
    import {useDashboardStore} from "../../../stores/dashboard";
    import Editor from "../../inputs/Editor.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {usePluginsStore} from "../../../stores/plugins";
    import AiCopilotWrapper from "../../ai/AiCopilotWrapper.vue";
    import AITriggerButton from "../../ai/AITriggerButton.vue";
    import {aiGenerationTypes} from "../../../utils/constants";
    import AcceptDecline from "../../inputs/AcceptDecline.vue";

    const dashboardStore = useDashboardStore();

    const pluginsStore = usePluginsStore();
    async function updatePluginDocumentation(event: any) {
        const type = YAML_UTILS.getTypeAtPosition(event.model.getValue(), event.position, plugins.value);
        if (type) {
            const plugin = await pluginsStore.load({cls: type});
            pluginsStore.editorPlugin = {cls: type, ...plugin};
        } else {
            pluginsStore.editorPlugin = undefined;
        }
    }

    async function updateChartPreview(event: any) {
        const chart = YAML_UTILS.getChartAtPosition(event.model.getValue(), event.position);
        if (chart) {
            const result = await dashboardStore.loadChart(chart);
            dashboardStore.selectedChart = typeof result.data === "object"
                ? {
                    ...result.data,
                    chartOptions: {
                        ...result.data?.chartOptions,
                        width: 12
                    }
                } as any
                : undefined;
            dashboardStore.chartErrors = [result.error].filter(e => e !== null);
        }
    }

    function cursor(event: any) {
        updatePluginDocumentation(event);
        updateChartPreview(event);
    }

    const plugins = ref<string[]>([]);
    async function loadPlugins() {
        const data = await pluginsStore.list();
        plugins.value = data.map((plugin: any) => {
            const charts = plugin.charts || [];
            const dataFilters = plugin.dataFilters || [];
            return charts.concat(dataFilters);
        }).flat()
            .filter(({deprecated}: any) => !deprecated)
            .map(({cls}: any) => cls);
    }

    onMounted(() => {
        loadPlugins();
    });

    const copilotWrapper = ref<InstanceType<typeof AiCopilotWrapper>>();
    const draftSource = ref<string | undefined>(undefined);

    const editorContent = computed<string>({
        get: () => draftSource.value ?? (dashboardStore.sourceCode as unknown as string),
        set: (value: string) => {
            if (draftSource.value !== undefined) {
                draftSource.value = value;
            } else {
                dashboardStore.sourceCode = value;
            }
        }
    });

    const hasDraft = computed(() => draftSource.value !== undefined);

    function acceptDraft() {
        const accepted = draftSource.value;
        draftSource.value = undefined;
        copilotWrapper.value?.resetConversation();
        if (accepted !== undefined) {
            dashboardStore.sourceCode = accepted;
        }
    }

    function declineDraft() {
        draftSource.value = undefined;
        copilotWrapper.value?.openAiCopilot();
    }
</script>

