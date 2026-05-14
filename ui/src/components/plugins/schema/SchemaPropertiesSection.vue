<template>
    <SchemaSection
        :class="['section-collapsible', {nested}]"
        :clickableText="sectionName"
        :href="href"
        :initiallyExpanded="initiallyExpanded || autoExpanded"
        :noUrlChange="noUrlChange"
        @expand="emit('expand')"
    >
        <template v-if="Object.keys(properties ?? {}).length > 0" #content>
            <div v-if="description || (examples && examples.length > 0)" class="section-intro">
                <div v-if="description" class="markdown">
                    <slot name="markdown" :content="description" />
                </div>
                <div v-if="examples && examples.length > 0" class="examples-container">
                    <h6 class="examples-heading">
                        Examples
                    </h6>
                    <div v-for="(example, idx) in examples" :key="idx" class="example-item">
                        <slot name="example" :example="example" />
                    </div>
                </div>
            </div>

            <div class="properties-list">
                <SchemaSection
                    v-for="(property, propertyKey) in sortedAndAggregated(properties)"
                    :key="propertyKey"
                    class="property"
                    :arrow="false"
                    :clickableText="String(propertyKey)"
                    :href="`${href}_${propertyKey}`"
                    :noUrlChange
                    @expand="autoExpanded = true"
                >
                    <template #additionalButtonText>
                        <KsTooltip v-if="property['$required']" content="Required">
                            <span class="property-flag property-flag--required"> *</span>
                        </KsTooltip>
                    </template>
                    <template #buttonRight="{collapsed}">
                        <span class="property-button-right">
                            <span class="property-flags">
                                <KsTooltip v-if="showDynamic && !isDynamic(property)" content="Non-dynamic">
                                    <Snowflake class="property-flag property-flag--info" />
                                </KsTooltip>
                                <KsTooltip v-if="property['$beta']" content="Beta">
                                    <AlphaBBox class="property-flag property-flag--warning" />
                                </KsTooltip>
                                <KsTooltip v-if="property['$deprecated']" content="Deprecated">
                                    <Alert class="property-flag property-flag--warning" />
                                </KsTooltip>
                            </span>
                            <span class="property-types">
                                <template v-for="type in nonDeprecatedTypes(extractTypeInfo(property).types)" :key="type">
                                    <a v-if="type.startsWith('#')" class="ref-type-box" :href="type" @click.stop>
                                        <span class="ref-type">{{ className(type) }}</span><EyeOutline />
                                    </a>
                                    <span v-else class="type-box">{{ type }}</span>
                                </template>
                                <component :is="collapsed ? ChevronDown : ChevronUp" class="arrow" />
                            </span>
                        </span>
                    </template>
                    <template #content>
                        <PropertyDetail :property="property">
                            <template #markdown="{content}">
                                <slot :content="content" name="markdown" />
                            </template>
                        </PropertyDetail>
                    </template>
                </SchemaSection>
            </div>
        </template>
    </SchemaSection>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {KsTooltip} from "@kestra-io/design-system"
    import Alert from "vue-material-design-icons/Alert.vue"
    import AlphaBBox from "vue-material-design-icons/AlphaBBox.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
    import Snowflake from "vue-material-design-icons/Snowflake.vue"
    import SchemaSection from "./SchemaSection.vue"
    import PropertyDetail from "./PropertyDetail.vue"
    import {
        aggregateAllOf,
        className,
        extractTypeInfo,
        isDeprecated,
        isDynamic,
        type JSONProperty,
        type JSONSchema,
        type SchemaExample,
    } from "./utils/schemaUtils"

    const props = withDefaults(defineProps<{
        href?: string;
        sectionName: string;
        properties?: Record<string, JSONProperty>;
        definitions?: Record<string, JSONSchema>;
        showDynamic?: boolean;
        initiallyExpanded?: boolean;
        forceInclude?: string[];
        noUrlChange?: boolean;
        description?: string;
        examples?: SchemaExample[];
        nested?: boolean;
    }>(), {
        href: () => Math.random().toString(36).substring(2, 5),
        properties: undefined,
        definitions: undefined,
        showDynamic: true,
        initiallyExpanded: false,
        forceInclude: () => [],
        noUrlChange: false,
        description: undefined,
        examples: undefined,
        nested: false,
    })

    const emit = defineEmits<{expand: []}>()

    const autoExpanded = ref(false)

    watch(autoExpanded, (expanded) => {
        if (expanded) emit("expand")
    })

    const nonDeprecatedTypes = (types: string[]) =>
        types.filter((type) => !type.startsWith("#") || !isDeprecated(props.definitions?.[type.slice(1)]))

    function sortedAndAggregated(schema?: Record<string, JSONProperty>): Record<string, JSONProperty> {
        const source = schema ?? {}
        const requiredKeys: string[] = []
        const nonRequiredKeys: string[] = []

        for (const key of Object.keys(source)) {
            if (typeof source[key] === "object") {
                source[key] = aggregateAllOf(source[key]);
                (source[key].$required ? requiredKeys : nonRequiredKeys).push(key)
            }
        }

        const sortedKeys = [...requiredKeys.sort(), ...nonRequiredKeys.sort()]
        const sortedSchema: Record<string, JSONProperty> = {}

        for (const key of sortedKeys) {
            if (!source[key].$deprecated || props.forceInclude?.includes(key)) {
                sortedSchema[key] = source[key]
            }
        }

        return sortedSchema
    }
</script>

<style lang="scss" scoped>
    .nested :deep(p) {
        font-size: var(--ks-font-size-sm);
        line-height: 22px;
        font-weight: normal;
    }

    .property-flag {
        display: inline-flex;
        align-items: center;

        &--required {
            color: var(--ks-content-error);
        }

        &--info {
            color: var(--ks-content-info);
        }

        &--warning {
            color: var(--ks-content-warning);
        }
    }

    .property-button-right {
        display: flex;
        flex: 1 1 auto;
        align-items: center;
        justify-content: space-between;
    }

    .property-flags {
        display: flex;
        gap: 0.5rem;
    }

    .property-types {
        display: flex;
        flex-wrap: wrap;
        justify-content: flex-end;
        gap: 0.5rem;
    }

    .type-box {
        background-color: var(--ks-tag-background-active);
        color: var(--ks-tag-content);
        font-size: 12px;
        padding: 0 8px 2px;
        border-radius: 8px;
        text-transform: capitalize;
    }

    .ref-type-box {
        display: flex;
        align-items: center;
        font-weight: 700;
        font-size: var(--ks-font-size-xs);
        line-height: 1;
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--ks-border-info);
        border-radius: 0.25rem;
        background: transparent;
        color: var(--ks-content-primary);

        .ref-type + * {
            margin-left: 0.625rem;
        }
    }

    .section-intro {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        margin: 0.5rem 0 1rem;
    }

    .examples-heading {
        font-weight: 700;
        margin-bottom: 0.5rem;
    }

    .example-item {
        margin-bottom: 0.5rem;
    }

    .properties-list {
        overflow: hidden;
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.5rem;
        margin-top: 0.75rem;
    }

    .property {
        gap: 0 !important;
        background-color: var(--ks-dropdown-background);
        border-bottom: 1px solid var(--ks-border-primary);

        &:last-child {
            border-bottom: 0;
        }

        :deep(> .collapse-button) {
            padding: .75rem 1rem;

            &:not(.collapsed) {
                border-bottom: 1px solid var(--collapsible-border-color, var(--ks-border-primary));
            }
        }

        :deep(.property-detail) {
            background-color: var(--ks-background-body);
            padding: 1rem 0;

            > * {
                padding-left: 1rem;
                padding-right: 1rem;
            }

            button:hover {
                background-color: var(--ks-dropdown-background-hover);
            }
        }
    }
</style>
