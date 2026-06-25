<template>
    <header v-if="blueprint" class="header">
        <div class="bar">
            <KsButton :icon="ChevronLeft" link @click="emit('back')">
                {{ $t("blueprints.title") }}
            </KsButton>
            <div v-if="$slots.actions" class="actions">
                <slot name="actions" />
            </div>
        </div>
        <h2 class="title">{{ blueprint.title }}</h2>
        <p v-if="blueprint.shortDescription" class="subtitle">
            {{ blueprint.shortDescription }}
        </p>
    </header>

    <section class="content" v-ks-loading="!blueprint">
        <template v-if="blueprint">
            <KsCard>
                <KsEditor
                    v-bind="editorBindings"
                    class="position-relative"
                    lang="yaml"
                    readOnly
                    inline
                    :navbar="false"
                    :options="{fullHeight: false}"
                    :modelValue="blueprint.source"
                >
                    <template #absolute>
                        <CopyToClipboard :text="blueprint.source" />
                    </template>
                </KsEditor>
            </KsCard>

            <KsMarkdown v-if="blueprint.description" class="markdown" :content="blueprint.description" />

            <BlueprintOverview :blueprint :tags :icons :columns="2" />
        </template>
    </section>
</template>

<script setup lang="ts">
    import {KsEditor} from "@kestra-io/design-system"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"

    import CopyToClipboard from "../../layout/CopyToClipboard.vue"
    import BlueprintOverview from "./BlueprintOverview.vue"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const props = withDefaults(defineProps<{
        blueprint?: FlowBlueprint & {shortDescription?: string};
        tags?: Record<string, BlueprintTag>;
        icons?: Record<string, any>;
        kind?: string;
    }>(), {
        blueprint: undefined,
        tags: undefined,
        icons: () => ({}),
        kind: "flow",
    })

    const emit = defineEmits<{back: []}>()

    const editorBindings = useEditorBindings()
</script>

<style scoped lang="scss">
    .header {
        margin: 0 0 var(--ks-spacing-4);

        .bar {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            margin-bottom: var(--ks-spacing-2);

            .actions {
                margin-left: auto;
            }
        }

        .title {
            margin: 0;
            margin-top: 1.5rem;
            overflow: hidden;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-lg);
            font-weight: var(--ks-font-weight-bold);
            line-height: 1.875rem;
            text-overflow: ellipsis;
        }

        .subtitle {
            margin: 0;
            margin-top: 0.5rem;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-md);
            line-height: 26px;
            font-weight: var(--ks-font-weight-regular);
        }
    }

    .content {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        min-height: 100%;

        :deep(.kel-card__body) {
            padding: 0;
        }

        .markdown {
            padding: var(--ks-spacing-7) var(--ks-spacing-4) var(--ks-spacing-6);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
            background: var(--ks-bg-surface);
            box-shadow: 0 2px 8px 0 var(--ks-shadow-surface);
        }
    }
</style>