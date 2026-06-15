<template>
    <div class="property-detail">
        <div v-if="subtype">
            <span>SubType</span>
            <a v-if="subtype.startsWith('#')" :href="subtype" class="ref-type-link" @click.stop>
                <KsTag type="info">
                    {{ className(subtype) }}
                    <template #icon>
                        <EyeOutline />
                    </template>
                </KsTag>
            </a>
            <KsTag v-else>
                {{ subtype }}
            </KsTag>
        </div>

        <template v-for="row in VALUE_ROWS" :key="row.label">
            <div v-if="isVisible(row)">
                <span>{{ row.label }}</span>
                <code class="value-pill">
                    {{ formatValue(row) }}
                </code>
            </div>
        </template>

        <div v-if="enumValues !== undefined">
            <span>Possible Values</span>
            <div class="enum-values">
                <KsTag v-for="(possibleValue, index) in enumValues" :key="index">
                    {{ possibleValue }}
                </KsTag>
            </div>
        </div>

        <div v-if="property.title !== undefined || property.description !== undefined">
            <div class="property-description markdown">
                <slot
                    v-if="property.title !== undefined"
                    name="markdown"
                    :content="sanitizeForMarkdown(property.title)"
                />
                <slot
                    v-if="property.description !== undefined"
                    name="markdown"
                    :content="sanitizeForMarkdown(property.description)"
                />
                <div v-if="property['$internalStorageURI']">
                    <KsAlert type="info" :closable="false">
                        <slot
                            name="markdown"
                            :content="INTERNAL_STORAGE_URI_HINT"
                        />
                    </KsAlert>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {className, extractEnumValues, extractTypeInfo, sanitizeForMarkdown, type JSONProperty} from "./utils/schemaUtils"
    import {KsAlert, KsTag} from "@kestra-io/design-system"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"

    const INTERNAL_STORAGE_URI_HINT = "Pebble expression referencing an Internal Storage URI e.g. `{{ outputs.mytask.uri }}`."

    type ValueRow = {
        key: keyof JSONProperty;
        label: string;
        format?: (value: JSONProperty[keyof JSONProperty]) => string;
        show?: (value: JSONProperty[keyof JSONProperty]) => boolean;
    };

    const VALUE_ROWS: readonly ValueRow[] = [
        {key: "default", label: "Default"},
        {key: "pattern", label: "Validation RegExp"},
        {key: "unit", label: "Unit", show: (value) => typeof value === "string" && value.trim().length > 0},
        {key: "minLength", label: "Min length"},
        {key: "maxLength", label: "Max length"},
        {key: "minItems", label: "Min items"},
        {key: "maxItems", label: "Max items"},
        {key: "minimum", label: "Minimum", format: (value) => `>= ${value}`},
        {key: "exclusiveMinimum", label: "Minimum", format: (value) => `> ${value}`},
        {key: "maximum", label: "Maximum", format: (value) => `<= ${value}`},
        {key: "exclusiveMaximum", label: "Maximum", format: (value) => `< ${value}`},
        {key: "format", label: "Format"},
    ]

    const props = defineProps<{property: JSONProperty}>()

    const subtype = extractTypeInfo(props.property).subType
    const enumValues = extractEnumValues(props.property)

    const isVisible = (row: ValueRow) => {
        const value = props.property[row.key]
        return value !== undefined && (row.show?.(value) ?? true)
    }

    const formatValue = (row: ValueRow) => {
        const value = props.property[row.key]
        return row.format ? row.format(value) : value
    }
</script>

<style lang="scss" scoped>
    .property-detail > * {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: var(--spacer);
        padding: 1rem 0;
        border-top: 1px solid var(--ks-border-default);

        span, .property-description:deep(p) {
            line-height: 1.5rem;
            font-size: var(--ks-font-size-sm) !important;
        }

        .property-description {
            color: var(--ks-text-secondary);
        }

        code {
            color: var(--ks-text-primary);
            background: var(--ks-bg-surface) !important;
        }

        &:first-child {
            padding-top: 0;
            border-top: none !important;
        }

        &:last-child {
            padding-bottom: 0;
        }

        > * {
            width: fit-content;
        }
    }

    .ref-type-link {
        display: inline-flex;
        text-decoration: none;
    }

    .value-pill {
        font-size: var(--ks-font-size-xs);
        line-height: 1;
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }

    .enum-values {
        display: flex;
        flex-wrap: wrap;
        justify-content: flex-end;
        gap: 2rem;
    }
</style>
