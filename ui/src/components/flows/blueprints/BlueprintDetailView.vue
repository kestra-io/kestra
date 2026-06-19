<template>
    <section class="blueprint-detail">
        <div class="preview">
            <header>
                <div class="bar">
                    <KsButton :icon="ChevronLeft" link @click="emit('back')">
                        {{ $t("common.back") }}
                    </KsButton>
                    <div class="actions">
                        <slot name="actions" />
                    </div>
                </div>
                <div class="info">
                    <h1 class="title">{{ blueprint.title }}</h1>
                    <p v-if="blueprint.shortDescription" class="subtitle">
                        {{ blueprint.shortDescription }}
                    </p>
                </div>
            </header>

            <KsSplitter
                class="graph"
                :class="{stacked}"
                :layout="stacked ? 'vertical' : 'horizontal'"
            >
                <KsSplitterPanel :size="flowGraph ? '50%' : '100%'" min="20%">
                    <KsEditor
                        v-bind="editorBindings"
                        class="pane"
                        lang="yaml"
                        readOnly
                        :navbar="false"
                        :options="EDITOR_OPTIONS"
                        :modelValue="blueprint.source"
                    >
                        <template #absolute>
                            <CopyToClipboard :text="blueprint.source" />
                        </template>
                    </KsEditor>
                </KsSplitterPanel>
                <KsSplitterPanel v-if="flowGraph" min="20%">
                    <LowCodeEditor
                        class="pane"
                        viewType="blueprints"
                        isReadOnly
                        :flowId="parsedFlow.id"
                        :namespace="parsedFlow.namespace"
                        :flowGraph="flowGraph"
                        :source="blueprint.source"
                        :horizontalDefault="stacked"
                    />
                </KsSplitterPanel>
            </KsSplitter>
        </div>

        <BlueprintOverview :blueprint :tags :icons />

        <KsMarkdown
            v-if="blueprint.description"
            class="markdown"
            :content="blueprint.description"
        />
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useMediaQuery} from "@vueuse/core"

    import {KsEditor} from "@kestra-io/design-system"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"

    import LowCodeEditor from "../../inputs/LowCodeEditor.vue"
    import CopyToClipboard from "../../layout/CopyToClipboard.vue"
    import BlueprintOverview from "./BlueprintOverview.vue"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const EDITOR_OPTIONS = {
        fullHeight: true,
        editor: {
            padding: {top: 16, bottom: 16},
        },
    }

    const props = withDefaults(defineProps<{
        blueprint: FlowBlueprint & {shortDescription?: string};
        flowGraph?: any;
        tags?: Record<string, BlueprintTag>;
        icons?: Record<string, any>;
    }>(), {
        flowGraph: undefined,
        tags: undefined,
        icons: () => ({}),
    })

    const emit = defineEmits<{back: []}>()

    const editorBindings = useEditorBindings()
    const stacked = useMediaQuery("(max-width: 1400px)")

    const parsedFlow = computed(() =>
        props.blueprint.source
            ? {...YAML_UTILS.parse(props.blueprint.source), source: props.blueprint.source}
            : {},
    )
</script>

<style scoped lang="scss">
    .blueprint-detail {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 16.1875rem;
        gap: var(--ks-spacing-5) var(--ks-spacing-6);
        align-items: start;
    }

    .preview {
        grid-column: 1;
        grid-row: 1;
        overflow: hidden;
        border-radius: var(--ks-radius-base);
        box-shadow: 0 2px 8px 0 var(--ks-shadow-surface);

        header {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-3);
            padding: var(--ks-spacing-3) var(--ks-spacing-5) var(--ks-spacing-6) var(--ks-spacing-2);
            border-width: 1px 1px 0;
            border-style: solid;
            border-color: var(--ks-border-default);
            background: var(--ks-bg-surface);
        }

        .bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-3);
        }

        .actions {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
        }

        .info {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-2);
            padding: var(--ks-spacing-4) 0 0 var(--ks-spacing-5);
        }

        .title {
            margin: 0;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-xl);
            font-weight: var(--ks-font-weight-bold);
        }

        .subtitle {
            margin: 0;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-md);
        }
    }

    .graph {
        height: 65vh;
        border: 1px solid var(--ks-border-default);
        overflow: hidden;

        &.stacked {
            height: 100vh;
        }

        .pane {
            position: relative;
            height: 100%;
        }
    }

    .markdown {
        grid-column: 1;
        grid-row: 2;
        padding: var(--ks-spacing-7) var(--ks-spacing-6) var(--ks-spacing-6);
        border-bottom: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
    }

    .overview {
        grid-column: 2;
        grid-row: 1 / 3;
    }

    @media (max-width: 1300px) {
        .blueprint-detail {
            grid-template-columns: minmax(0, 1fr);
        }

        .preview {
            grid-column: 1;
            grid-row: 1;
        }

        .markdown {
            grid-column: 1;
            grid-row: 2;
        }

        .overview {
            grid-column: 1;
            grid-row: 3;
        }
    }
</style>