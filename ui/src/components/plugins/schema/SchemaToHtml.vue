<template>
    <div class="schema-root">
        <div class="schema-header">
            <div v-if="schema.properties?.$beta" class="beta-banner" role="alert">
                <p>
                    This plugin is currently in beta. While it is considered safe for use, please be aware that its API
                    could change in ways that are not compatible with earlier versions in future releases, or it might
                    become unsupported.
                </p>
            </div>

            <div v-if="schema.properties?.title" class="plugin-title markdown">
                <slot name="markdown" :content="normalizeColons(schema.properties.title)" />
            </div>
            <div v-if="schema.properties?.description" class="markdown">
                <slot name="markdown" :content="normalizeColons(schema.properties.description)" />
            </div>

            <SchemaToCode
                :key="pluginType"
                :highlighter="highlighter"
                language="yaml"
                :theme="codeTheme"
                :code="`type: ${pluginType}`"
            />
        </div>

        <div :key="pluginType" class="schema-sections">
            <SchemaSection
                v-if="examples"
                class="plugin-section"
                clickableText="Examples"
                href="examples"
                :noUrlChange
            >
                <template #content>
                    <div class="examples-list">
                        <template v-for="(example, index) in examples" :key="`${pluginType}-${index}`">
                            <div class="example-block">
                                <div class="markdown">
                                    <slot
                                        v-if="example.title"
                                        name="markdown"
                                        :content="normalizeColons(example.title)"
                                    />
                                </div>
                                <SchemaToCode
                                    v-if="example.code"
                                    :highlighter="highlighter"
                                    :language="example.lang ?? 'yaml'"
                                    :theme="codeTheme"
                                    :code="generateExampleCode(example)"
                                />
                            </div>
                            <hr v-if="index < examples.length - 1" class="example-divider">
                        </template>
                    </div>
                </template>
            </SchemaSection>

            <SchemaPropertiesSection
                v-if="schema.properties?.properties"
                class="plugin-section"
                :properties="schema.properties.properties"
                :definitions="schema.definitions"
                sectionName="Properties"
                href="properties"
                :initiallyExpanded="propsInitiallyExpanded"
                :forceInclude="forceIncludeProperties"
                :noUrlChange
            >
                <template #markdown="{content}">
                    <div class="markdown">
                        <slot name="markdown" :content="content" />
                    </div>
                </template>
            </SchemaPropertiesSection>

            <SchemaPropertiesSection
                v-if="schema.outputs?.properties && Object.keys(schema.outputs.properties).length > 0"
                class="plugin-section"
                :properties="schema.outputs.properties"
                :definitions="schema.definitions"
                sectionName="Outputs"
                href="outputs"
                :showDynamic="false"
                :noUrlChange
            >
                <template #markdown="{content}">
                    <div class="markdown">
                        <slot name="markdown" :content="content" />
                    </div>
                </template>
            </SchemaPropertiesSection>

            <SchemaPropertiesSection
                v-if="schema.properties?.$metrics"
                class="plugin-section"
                :properties="metrics"
                :definitions="schema.definitions"
                sectionName="Metrics"
                href="metrics"
                :showDynamic="false"
                :noUrlChange
            >
                <template #markdown="{content}">
                    <div class="markdown">
                        <slot name="markdown" :content="content" />
                    </div>
                </template>
            </SchemaPropertiesSection>

            <SchemaSection
                v-if="nonDeprecatedDefinitions.length > 0"
                :key="`definitions-${pluginType}-${forceExpandKey}`"
                class="plugin-section"
                clickableText="Definitions"
                href="definitions"
                :initiallyExpanded="definitionsExpanded"
                :noUrlChange
            >
                <template #content>
                    <div class="definitions-list">
                        <SchemaPropertiesSection
                            v-for="[definitionKey, definitionValue] in nonDeprecatedDefinitions"
                            :key="`${pluginType}-${definitionKey}`"
                            class="plugin-section"
                            nested
                            :properties="definitionValue.properties"
                            :definitions="schema.definitions"
                            :sectionName="definitionValue.title ?? definitionKey.split('_')[0]"
                            :href="definitionKey"
                            :showDynamic="false"
                            :initiallyExpanded="expandedDefinitions.has(definitionKey)"
                            :noUrlChange
                            :description="definitionValue.description"
                            :examples="definitionValue?.$examples"
                            @expand="onDefinitionExpand(definitionKey)"
                        >
                            <template #markdown="{content}">
                                <div class="markdown">
                                    <slot name="markdown" :content="content" />
                                </div>
                            </template>

                            <template #example="{example}">
                                <div class="example-block-tight">
                                    <div v-if="example.title" class="markdown">
                                        <slot name="markdown" :content="`**${example.title}**`" />
                                    </div>
                                    <SchemaToCode
                                        v-if="example.code"
                                        :highlighter="highlighter"
                                        :language="example.lang ?? 'yaml'"
                                        :theme="codeTheme"
                                        :code="generateExampleCode(example)"
                                    />
                                </div>
                            </template>
                        </SchemaPropertiesSection>
                    </div>
                </template>
            </SchemaSection>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, nextTick, onMounted, onUnmounted, ref, watch} from "vue";
    import type {HighlighterCore} from "shiki/core";
    import SchemaSection from "./SchemaSection.vue";
    import SchemaPropertiesSection from "./SchemaPropertiesSection.vue";
    import SchemaToCode from "./SchemaToCode.vue";
    import {getHighlighterCore} from "./shikiToolset";
    import {isDeprecated, type JSONProperty, type JSONSchema, type SchemaExample} from "./utils/schemaUtils";

    const COLON_NORMALIZE_REGEX = /(?<!:):(?![: /])/g;
    const MAX_SCROLL_ATTEMPTS = 30;

    const props = withDefaults(defineProps<{
        schema: JSONSchema;
        pluginType: string;
        darkMode?: boolean;
        propsInitiallyExpanded?: boolean;
        forceIncludeProperties?: string[];
        noUrlChange?: boolean;
    }>(), {
        darkMode: true,
        propsInitiallyExpanded: false,
        forceIncludeProperties: () => [],
        noUrlChange: false,
    });

    const definitionsExpanded = ref(false);
    const expandedDefinitions = ref<Set<string>>(new Set());
    const forceExpandKey = ref(0);
    const highlighter: HighlighterCore = await getHighlighterCore();

    const codeTheme = computed(() => `github-${props.darkMode ? "dark" : "light"}`);

    const examples = computed(() => props.schema.properties?.$examples);

    const metrics = computed<Record<string, JSONProperty>>(() => Object.fromEntries(
        props.schema.properties?.$metrics?.map((metric) => [metric.name, {...metric, name: undefined}]) ?? [],
    ));

    const nonDeprecatedDefinitions = computed(() =>
        Object.entries(props.schema.definitions ?? {}).filter(([, value]) => !isDeprecated(value)),
    );

    const normalizeColons = (text: string) => text.replace(COLON_NORMALIZE_REGEX, ": ");

    const generateExampleCode = (example: SchemaExample) => {
        if (example?.full) return example.code;

        const id = props.pluginType.split(".").pop()?.toLowerCase();
        return `id: ${id}\ntype: ${props.pluginType}\n${example.code}`;
    };

    const onDefinitionExpand = (definitionKey: string) => {
        definitionsExpanded.value = true;
        expandedDefinitions.value.add(definitionKey);
    };

    const checkHashAndExpand = async () => {
        const hash = window.location.hash.slice(1);
        if (!hash || !props.schema.definitions) {
            expandedDefinitions.value.clear();
            return;
        }

        const cleanHash = hash.replace(/-body$/, "");
        const definitionKey = Object.keys(props.schema.definitions).find((defKey) =>
            cleanHash === defKey || cleanHash.startsWith(`${defKey}_`),
        );

        if (!definitionKey) {
            expandedDefinitions.value.clear();
            return;
        }

        definitionsExpanded.value = true;
        forceExpandKey.value += 1;
        expandedDefinitions.value.clear();
        expandedDefinitions.value.add(definitionKey);

        await nextTick();

        let attempts = 0;
        const attemptScroll = () => {
            const element = document.getElementById(cleanHash);
            if (element) {
                element.scrollIntoView({behavior: "smooth", block: "start"});
            } else if (attempts < MAX_SCROLL_ATTEMPTS) {
                attempts++;
                requestAnimationFrame(attemptScroll);
            }
        };

        requestAnimationFrame(attemptScroll);
    };

    watch([() => props.schema, () => props.pluginType], () => {
        if (props.schema.definitions) {
            checkHashAndExpand();
        }
    });

    onMounted(() => {
        checkHashAndExpand();
        window.addEventListener("hashchange", checkHashAndExpand);
    });

    onUnmounted(() => {
        window.removeEventListener("hashchange", checkHashAndExpand);
    });
</script>

<style scoped lang="scss">
    .schema-root {
        display: flex;
        flex-direction: column;
        gap: 2rem;
    }

    .schema-header,
    .schema-sections {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }

    .beta-banner {
        display: flex;
        padding: 0.5rem !important;
        margin-bottom: 0.5rem;
        background-color: var(--ks-background-info);
        border: 1px solid var(--ks-border-info);
        border-left-width: 0.25rem;
        border-radius: 0.5rem;

        p {
            color: var(--ks-content-info);
        }
    }

    .plugin-title {
        font-size: var(--ks-font-size-lg);

        :deep(p) {
            font-size: var(--ks-font-size-base);
        }
    }

    .examples-list {
        display: flex;
        flex-direction: column;
    }

    .example-block {
        display: flex;
        flex-direction: column;
    }

    .example-block-tight {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
    }

    .example-divider {
        width: 100%;
        align-self: center;
    }

    .definitions-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        padding-left: 1rem;
    }

    :deep(.markdown) {
        display: flex;
        flex-direction: column;
        gap: 1rem;

        pre, .code-block {
            margin: 0;
        }

        > ol, > ul, > dl {
            margin-top: 0;
            margin-bottom: 0;
        }
    }

    :deep(.plugin-section) {
        p {
            margin-bottom: 0;
        }

        .collapse-button {
            font-size: var(--ks-font-size-lg);
            line-height: 1.5rem;
        }

        .material-design-icon {
            &, & * {
                height: 1.5rem;
                width: 1.5rem;
                bottom: 0;
            }
        }

        .material-design-icon:not(.property .material-design-icon) {
            &, & * {
                height: 2rem;
                width: 2rem;
            }
        }

        > .collapse-button:not(.collapsed) {
            color: var(--ks-content-link);
        }

        [id$="-body"]:not(#examples-body) span {
            font-size: var(--ks-font-size-sm);
            font-weight: 400;
        }
    }
</style>
