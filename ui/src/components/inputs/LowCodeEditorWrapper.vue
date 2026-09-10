<template>
    <div id="topologyWrapper" v-ks-loading="isLoading" class="vue-flow">
        <LowCodeEditor
            v-if="flowGraph"
            :flowGraph="flowGraph"
            :flowId="flowId"
            :namespace="namespace"
            :isReadOnly="isReadOnly"
            :source="flowYaml"
            :isAllowedEdit="isAllowedEdit"
            :expandedSubflows="expandedSubflows"
            @on-edit="onEdit"
            @loading="loadingState"
            @expand-subflow="onExpandSubflow"
        />
        <div v-else-if="invalidGraph">
            <KsAlert
                :title="$t('topology-graph.invalid')"
                type="error"
                class="invalid-graph"
                :closable="false"
            >
                {{ $t('topology-graph.invalid_description') }}
            </KsAlert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import LowCodeEditor from "./LowCodeEditor.vue"
    import {useFlowStore} from "../../stores/flow"
    import {useToast} from "../../utils/toast"

    const flowStore = useFlowStore()
    const toast = useToast()
    const {t} = useI18n()

    const flowYaml = computed(() => flowStore.flowYaml)
    const flowGraph = computed(() => flowStore.flowGraph)
    const invalidGraph = computed(() => flowStore.invalidGraph)
    const flowId = computed(() => flowStore.flow?.id)
    const namespace = computed(() => flowStore.flow?.namespace)
    const expandedSubflows = computed<string[]>(() => flowStore.expandedSubflows)
    const isAllowedEdit = computed(() => flowStore.isAllowedEdit)
    const isReadOnly = computed(() => flowStore.isReadOnly)

    const isLoading = ref(false)

    function loadingState(loading: boolean) {
        isLoading.value = loading
    }

    const onExpandSubflow = async (subflows: string[]) => {
        const previousExpandedSubflows = flowStore.expandedSubflows
        isLoading.value = true
        flowStore.expandedSubflows = subflows
        try {
            await flowStore.fetchGraph()
        } catch (error) {
            flowStore.expandedSubflows = previousExpandedSubflows
            const status = (error as {status?: number}).status
            if (![404, 422].includes(status ?? 0)) {
                toast.error(t("topology-graph.load_error"))
            }
            console.error("Failed to fetch expanded subflow graph:", error)
        } finally {
            isLoading.value = false
        }
    }

    const onEdit = async (source: string, currentIsFlow = false) => {
        flowStore.flowYaml = source
        const result = await flowStore.onEdit({
            source,
            editorViewType: "YAML",
            topologyVisible: true,
        })

        if (currentIsFlow && source) {
            await flowStore.loadGraphFromSource({
                flow: source,
            }).catch((error) => {
                console.error("Error loading graph:", error)
            })
        }

        return result
    }
</script>

<style scoped>
    .vue-flow {
        height: 100%;
    }
    :deep(.vue-flow__panel.bottom) {
        bottom: 2rem !important;
    }
    .invalid-graph {
        margin: 1rem;
        width: auto;
    }
</style>
