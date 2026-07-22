<template>
    <SchemaSection
        :class="['section-collapsible', {nested, compact}]"
        :style="labelColor ? {'--property-label-color': labelColor} : undefined"
        :clickableText="sectionName"
        :href="href"
        :arrow="!compact"
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

            <div v-if="showFilter" class="prop-filter-bar">
                <KsInput
                    v-model="filterText"
                    :placeholder="t('plugins.filter_properties')"
                    clearable
                    size="small"
                >
                    <template #prefix>
                        <Magnify class="prop-filter-icon" />
                    </template>
                </KsInput>
                <KsSegmented
                    v-model="filterMode"
                    size="small"
                    :options="filterOptions"
                />
            </div>

            <template v-if="compact">
                <div class="compact-props-list">
                    <template v-for="(property, propertyKey) in sortedAndAggregated(properties)" :key="propertyKey">
                        <div
                            v-show="isPropertyVisible(String(propertyKey), property)"
                            class="compact-prop"
                        >
                            <div class="compact-prop-top">
                                <span v-if="property['$required']" class="compact-req-dot" :title="t('plugins.required')" />
                                <span class="compact-prop-name">{{ String(propertyKey) }}</span>
                                <template v-for="type in nonDeprecatedTypes(extractTypeInfo(property).types)" :key="type">
                                    <a v-if="type.startsWith('#')" :href="type" class="compact-type-tag compact-type-tag-link" @click.stop>
                                        {{ className(type) }}
                                    </a>
                                    <span v-else class="compact-type-tag">{{ type }}</span>
                                </template>
                                <span v-if="property.default !== undefined" class="compact-meta-tag">{{ property.default }}</span>
                                <span v-if="showDynamic && !isDynamic(property)" class="compact-meta-tag compact-meta-tag-static">
                                    {{ t('plugins.non_dynamic') }}
                                </span>
                                <span v-if="showDynamic && isDynamic(property)" class="compact-meta-tag compact-meta-tag-dynamic">
                                    {{ t('plugins.dynamic') }}
                                </span>
                                <KsIcon
                                    v-if="property['$beta']"
                                    :tooltip="t('plugins.beta')"
                                    class="property-flag property-flag--warning"
                                >
                                    <AlphaBBox />
                                </KsIcon>
                                <KsIcon
                                    v-if="property['$deprecated']"
                                    :tooltip="t('plugins.deprecated')"
                                    class="property-flag property-flag--warning"
                                >
                                    <Alert />
                                </KsIcon>
                            </div>
                            <div
                                v-if="property.description || property.title"
                                class="compact-prop-desc"
                            >
                                <slot
                                    name="markdown"
                                    :content="property.title || property.description || ''"
                                />
                            </div>
                        </div>
                    </template>
                </div>
            </template>

            <template v-else>
                <div class="properties-list">
                    <template v-for="(property, propertyKey) in sortedAndAggregated(properties)" :key="propertyKey">
                        <SchemaSection
                            v-show="isPropertyVisible(String(propertyKey), property)"
                            class="property"
                            :clickableText="String(propertyKey)"
                            :href="`${href}_${propertyKey}`"
                            :noUrlChange
                            @expand="autoExpanded = true"
                        >
                            <template #additionalButtonText>
                                <KsIcon
                                    v-if="showDynamic && !isDynamic(property)"
                                    tooltip="Non-dynamic"
                                    class="property-flag property-flag--info"
                                >
                                    <Snowflake />
                                </KsIcon>
                                <KsTooltip v-if="property['$required']" content="Required">
                                    <span class="property-flag property-flag--required"> *</span>
                                </KsTooltip>
                            </template>
                            <template #buttonRight>
                                <span class="property-button-right">
                                    <span class="property-flags">
                                        <KsIcon
                                            v-if="property['$beta']"
                                            tooltip="Beta"
                                            class="property-flag property-flag--warning"
                                        >
                                            <AlphaBBox />
                                        </KsIcon>
                                        <KsIcon
                                            v-if="property['$deprecated']"
                                            tooltip="Deprecated"
                                            class="property-flag property-flag--warning"
                                        >
                                            <Alert />
                                        </KsIcon>
                                    </span>
                                    <span class="property-types">
                                        <template v-for="type in nonDeprecatedTypes(extractTypeInfo(property).types)" :key="type">
                                            <a v-if="type.startsWith('#')" :href="type" class="ref-type-link" @click.stop>
                                                <KsTag type="info">
                                                    {{ className(type) }}
                                                    <template #icon>
                                                        <EyeOutline />
                                                    </template>
                                                </KsTag>
                                            </a>
                                            <KsTag v-else>
                                                {{ type }}
                                            </KsTag>
                                        </template>
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
                    </template>
                </div>
            </template>
        </template>
    </SchemaSection>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsIcon, KsInput, KsSegmented, KsTag, KsTooltip} from "@kestra-io/design-system"
    import {useI18n} from "vue-i18n"
    import Alert from "vue-material-design-icons/Alert.vue"
    import AlphaBBox from "vue-material-design-icons/AlphaBBox.vue"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
    import Magnify from "vue-material-design-icons/Magnify.vue"
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
        labelColor?: string;
        showFilter?: boolean;
        compact?: boolean;
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
        labelColor: undefined,
        showFilter: false,
        compact: false,
    })

    const emit = defineEmits<{expand: []}>()

    const {t} = useI18n()
    const autoExpanded = ref(false)
    const filterText = ref("")
    const filterMode = ref<string | number | boolean>("all")

    const filterOptions = computed(() => [
        {label: t("plugins.filter_all"), value: "all"},
        {label: t("plugins.filter_required"), value: "required"},
    ])

    watch(autoExpanded, (expanded) => {
        if (expanded) emit("expand")
    })

    function isPropertyVisible(key: string, property: JSONProperty): boolean {
        if (!props.showFilter) return true
        const nameMatch = filterText.value === "" || key.toLowerCase().includes(filterText.value.toLowerCase())
        const modeMatch = filterMode.value === "all" || Boolean(property["$required"])
        return nameMatch && modeMatch
    }

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

    .nested :deep(> .collapse-button) {
        justify-content: flex-start;
        .collapse-button__chevron {
            order: -1;
            margin-left: 0;
        }
    }

    .property-flag {
        display: inline-flex;
        align-items: center;

        &--required {
            color: var(--ks-status-error);
        }

        &--info {
            color: var(--ks-status-info);
        }

        &--warning {
            color: var(--ks-status-warning);
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

    .ref-type-link {
        display: inline-flex;
        text-decoration: none;
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

    .prop-filter-bar {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-3);
    }

    .prop-filter-icon {
        font-size: var(--ks-icon-size-sm);
        color: var(--ks-icon-default);
        display: flex;
        align-items: center;
    }

    .section-collapsible.compact :deep(.collapse-button) {
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-bold);
        text-transform: uppercase;
        letter-spacing: 0.07em;
        pointer-events: none;
        margin-bottom: var(--ks-spacing-3);
    }

    .section-collapsible.compact :deep(.collapse-button__label) {
        color: var(--ks-text-muted);
    }

    .properties-list {
        overflow: hidden;
        border: 1px solid var(--ks-border-default);
        border-radius: 0.5rem;
        margin-top: 0.75rem;
    }

    .property {
        gap: 0 !important;
        border-bottom: 1px solid var(--ks-border-default);

        &:last-child {
            border-bottom: 0;
        }

        :deep(> .collapse-button > .collapse-button__label) {
            color: var(--property-label-color, inherit);
        }

        :deep(> .collapse-button) {
            padding: .75rem 1rem;

            &:not(.collapsed) {
                border-bottom: 1px solid var(--collapsible-border-color, var(--ks-border-default));
            }
        }

        :deep(.property-detail) {
            background-color: var(--ks-bg-base);
            padding: 1rem 0;

            > * {
                padding-left: 1rem;
                padding-right: 1rem;
            }

            button:hover {
                background-color: var(--ks-bg-hover);
            }
        }
    }

    .compact-props-list {
        margin-top: var(--ks-spacing-4);
    }

    .compact-prop {
        padding: var(--ks-spacing-5) 0;
        border-top: 1px solid var(--ks-border-default);

        &:first-child {
            border-top: none;
            padding-top: 0;
        }

        &:last-child {
            padding-bottom: 0;
        }
    }

    .compact-prop-top {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        flex-wrap: wrap;
    }

    .compact-req-dot {
        width: 6px;
        height: 6px;
        border-radius: 999px;
        background: var(--ks-status-error);
        flex: none;
        display: inline-block;
    }

    .compact-prop-name {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-base);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .compact-type-tag {
        display: inline-flex;
        align-items: center;
        height: 1.25rem;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-semibold);
        line-height: 1;
        color: var(--ks-text-link);
        background: var(--ks-bg-tag);
        padding: 0 var(--ks-spacing-2);
        border: 1px solid transparent;
        border-radius: var(--ks-radius-xs);
    }

    .compact-type-tag-link {
        text-decoration: none;
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-tag-active);
        }
    }

    .compact-meta-tag {
        display: inline-flex;
        align-items: center;
        height: 1.25rem;
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-semibold);
        line-height: 1;
        color: var(--ks-text-dim);
        background: var(--ks-bg-base);
        border: 1px solid var(--ks-border-default);
        padding: 0 var(--ks-spacing-2);
        border-radius: var(--ks-radius-xs);
    }

    .compact-meta-tag-dynamic {
        color: var(--ks-status-info);
        border-color: transparent;
        background: var(--ks-bg-info);
    }

    .compact-meta-tag-static {
        color: var(--ks-text-muted);
        border-color: transparent;
        background: transparent;
    }

    .compact-prop-desc {
        margin-top: var(--ks-spacing-2);
        font-size: var(--ks-font-size-base);
        line-height: 1.65;
        color: var(--ks-text-secondary);

        :deep(p) {
            margin: 0;
            font-size: var(--ks-font-size-base);
            line-height: 1.65;
            color: var(--ks-text-secondary);
        }

        :deep(p + p) {
            margin-top: var(--ks-spacing-2);
        }

        :deep(code) {
            font-family: var(--ks-font-family-mono);
            font-size: 0.92em;
            background: var(--ks-bg-tag);
            padding: 1px 4px;
            border-radius: var(--ks-radius-xs);
            color: var(--ks-text-dim);
        }
    }
</style>
