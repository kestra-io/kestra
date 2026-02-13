<template>
    <AiCopilot
        v-if="aiCopilotOpened"
        class="position-absolute prompt ai-copilot-popup"
        @close="closeAiCopilot"
        :flow="editorContent"
        :conversationId="conversationId"
        :generationType="aiGenerationTypes.DASHBOARD"
        @generated-yaml="(yaml: string) => {draftSource = yaml; aiCopilotOpened = false}"
    />
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
                @click="openAi"
            />
        </template>
        <template #footer-row>
            <AcceptDecline :visible="hasDraft" @accept="acceptDraft" @reject="declineDraft" />
        </template>
    </Editor>
</template>

<script lang="ts" setup>
    import {onMounted, ref, computed} from "vue";
    import {useDashboardStore} from "../../../stores/dashboard";
    import Editor from "../../inputs/Editor.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {usePluginsStore} from "../../../stores/plugins";
    import AiCopilot from "../../ai/AiCopilot.vue";
    import AITriggerButton from "../../ai/AITriggerButton.vue";
    import {useAuthStore} from "override/stores/auth";
    import permission from "../../../models/permission";
    import action from "../../../models/action";
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

    const authStore = useAuthStore();
    const aiCopilotOpened = ref(false);
    const draftSource = ref<string | undefined>(undefined);
    const conversationId = ref<string>(Date.now().toString(36) + Math.random().toString(36).slice(2, 8));

    const aiCopilotAllowed = computed(() => !!authStore.user?.hasAnyActionOnAnyNamespace(permission.AI_COPILOT, action.READ));

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

    function closeAiCopilot() {
        aiCopilotOpened.value = false;
    }

    function openAi() {
        draftSource.value = undefined;
        aiCopilotOpened.value = true;
    }

    function acceptDraft() {
        const accepted = draftSource.value;
        draftSource.value = undefined;
        conversationId.value = Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
        if (accepted !== undefined) {
            dashboardStore.sourceCode = accepted;
        }
    }

    function declineDraft() {
        draftSource.value = undefined;
        aiCopilotOpened.value = true;
    }
</script>


<style scoped lang="scss">

    .prompt {
        bottom: 10%;
        width: calc(100% - 5rem);
        left: 3rem;
        max-width: 700px;
        background-color: var(--ks-background-panel);
        box-shadow: 0 2px 4px 0 var(--ks-card-shadow);
    }


    .ai-copilot-popup {
        z-index: 1001;
        transform-origin: center bottom;
    }

</style>
