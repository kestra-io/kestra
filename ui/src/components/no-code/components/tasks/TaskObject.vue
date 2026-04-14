<template>
    <el-form labelPosition="top" class="w-100">
        <template v-if="sortedProperties">
            <template v-for="[fieldKey, fieldSchema] in protectedMainProperties" :key="fieldKey">
                <Wrapper :merge>
                    <template #tasks>
                        <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                    </template>
                </Wrapper>
            </template>

            <el-collapse v-model="activeNames" v-if="mainProperties.length && hasGroupedProperties" class="collapse">
                <el-collapse-item
                    v-for="section in groupSections"
                    :key="section.key"
                    :name="section.key"
                    :title="groupTitle(section.key)"
                >
                    <template v-for="[fieldKey, fieldSchema] in section.properties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>
                <el-collapse-item name="deprecated" v-if="deprecatedProperties?.length" :title="groupTitle('deprecated')">
                    <template v-for="[fieldKey, fieldSchema] in deprecatedProperties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>
            </el-collapse>
        </template>

        <template v-else-if="typeof modelValue === 'object' && modelValue !== null && !Array.isArray(modelValue)">
            <TaskDict
                :modelValue
                @update:model-value="
                    (value) => $emit('update:modelValue', value)
                "
                :root
                :schema="schema ?? {}"
                :required
            />
        </template>
    </el-form>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import TaskDict from "./TaskDict.vue";
    import Wrapper from "./Wrapper.vue";
    import TaskObjectField from "./TaskObjectField.vue";
    import {collapseEmptyValues} from "./MixinTask";
    import {DATA_TYPES_MAP_INJECTION_KEY} from "../../injectionKeys";

    defineOptions({
        inheritAttrs: false,
    });

    const {t, te} = useI18n();

    type Model = Record<string, any> | undefined;
    type Schema = { required?: string[]; [k: string]: any } | undefined;

    const props = defineProps<{
        merge?: boolean;
        properties?: any;
        metadataInputs?: boolean;
        modelValue?: Model;
        required?: boolean;
        schema?: Schema;
        root?: string;
        filterType?: boolean;
    }>();

    const emit = defineEmits<{
        (e: "update:modelValue", value: Model): void;
    }>();

    function groupTitle(key: string): string {
        const i18nKey = `no_code.sections.${key}`;
        if (te(i18nKey)) return t(i18nKey);
        // Free-form group: title-case, replace _ and - with spaces
        return key.replace(/[_-]/g, " ").replace(/\b\w/g, c => c.toUpperCase());
    }

    // Recommended group ordering — not exhaustive, unknown groups are appended alphabetically
    const GROUP_ORDER = ["connection", "source", "processing", "execution", "destination", "reliability", "advanced"];
    // Groups expanded by default
    const GROUPS_EXPANDED_BY_DEFAULT = new Set(["connection", "source", "destination"]);

    const activeNames = ref<string[]>([...GROUPS_EXPANDED_BY_DEFAULT, "optional"]);

    const FIRST_FIELDS = ["id", "forced", "on", "field", "type"];

    type Entry = [string, any];

    function sortProperties(properties: Entry[], required?: string[]): Entry[] {
        if (!properties?.length) return [];
        return properties.slice().sort((a, b) => {
            if (FIRST_FIELDS.includes(a[0]) && !FIRST_FIELDS.includes(b[0])) return -1;
            if (FIRST_FIELDS.includes(b[0]) && !FIRST_FIELDS.includes(a[0])) return 1;

            const aIndex = FIRST_FIELDS.indexOf(a[0]);
            const bIndex = FIRST_FIELDS.indexOf(b[0]);
            if(aIndex !== -1 && bIndex !== -1){
                return aIndex - bIndex;
            }

            const aRequired = (required || []).includes(a[0]);
            const bRequired = (required || []).includes(b[0]);

            if (aRequired && !bRequired) return -1;
            if (!aRequired && bRequired) return 1;

            const aDefault = "default" in a[1];
            const bDefault = "default" in b[1];

            if (aDefault && !bDefault) return 1;
            if (!aDefault && bDefault) return -1;

            return a[0].localeCompare(b[0]);
        });
    }

    function isDeprecated(value: any) {
        if(value?.allOf){
            return value.allOf.some(isDeprecated);
        }
        return value?.$deprecated;
    }

    function isPartOfGroup(value: any, groups: string[]) {
        if (value?.allOf) {
            return value.allOf.some((item: any) => isPartOfGroup(item, groups));
        }
        if (value?.anyOf) {
            return value.anyOf.some((item: any) => isPartOfGroup(item, groups));
        }
        return value?.$group && groups.includes(value.$group);
    }

    function getGroup(value: any): string | null {
        if (value?.allOf) {
            for (const item of value.allOf) {
                const g = getGroup(item);
                if (g) return g;
            }
        }
        if (value?.anyOf) {
            for (const item of value.anyOf) {
                const g = getGroup(item);
                if (g) return g;
            }
        }
        return value?.$group ?? null;
    }

    function getIndex(value: any): number {
        if (value?.allOf) {
            for (const item of value.allOf) {
                const i = getIndex(item);
                if (i !== -1) return i;
            }
        }
        return value?.$index ?? -1;
    }

    function sortByIndex(properties: Entry[]): Entry[] {
        return properties.slice().sort((a, b) => {
            const ai = getIndex(a[1]);
            const bi = getIndex(b[1]);
            if (ai !== -1 && bi !== -1) return ai - bi;
            if (ai !== -1) return -1;
            if (bi !== -1) return 1;
            return 0;
        });
    }

    const filteredProperties = computed<Entry[]>(() => {
        const propertiesProc = (props.properties ?? props.schema?.properties);
        return propertiesProc
            ? (Object.entries(propertiesProc) as Entry[]).filter(([key, value]) => {
                return value && !(props.filterType && key === "type") && !Array.isArray(value);
            })
            : [];
    });

    const sortedProperties = computed<Entry[]>(() => sortProperties(filteredProperties.value, props.schema?.required));

    const isRequired = (key: string) => Boolean(props.schema?.required?.includes(key));

    const dataTypesMap = inject(DATA_TYPES_MAP_INJECTION_KEY, ref<Record<string, string[] | undefined>>({}));

    const mainProperties = computed<Entry[]>(() => {
        const properties = props.merge
            ? sortedProperties.value
            : sortedProperties.value.filter(([p, v]) => v && (isRequired(p) || isPartOfGroup(v, ["main"])) && !isDeprecated(v));
        const dataTypes = dataTypesMap.value[props.root ?? ""];
        if (dataTypes) {
            properties.unshift(["type", {
                type: "string",
                enum: dataTypes,
                $required: true,
            }]);
        }
        return properties;
    });

    const protectedMainProperties = computed<Entry[]>(() => {
        return mainProperties.value.length ? mainProperties.value : sortedProperties.value;
    });

    type GroupEntry = { key: string; properties: Entry[] };

    // Dynamically build ordered group sections from whatever $group values are present.
    // Known groups follow GROUP_ORDER; unknown groups are appended alphabetically.
    // Ungrouped non-required properties (null group) land in an "optional" fallback section.
    const groupSections = computed<GroupEntry[]>(() => {
        if (props.merge) return [];

        const buckets = new Map<string | null, Entry[]>();

        for (const entry of sortedProperties.value) {
            const [p, v] = entry;
            if (!v || isRequired(p) || isPartOfGroup(v, ["main"]) || isDeprecated(v)) continue;

            const group = getGroup(v);
            if (group === "main") continue;

            if (!buckets.has(group)) buckets.set(group, []);
            buckets.get(group)!.push(entry);
        }

        const sorted = (g: string | null) => sortByIndex(buckets.get(g)!);
        const known = GROUP_ORDER.filter(g => buckets.has(g)).map(g => ({key: g, properties: sorted(g)}));
        const unknown = [...buckets.keys()]
            .filter((g): g is string => g !== null && !GROUP_ORDER.includes(g))
            .sort()
            .map(g => ({key: g, properties: sorted(g)}));
        const ungrouped = buckets.has(null) ? [{key: "optional", properties: sorted(null)}] : [];

        return [...known, ...unknown, ...ungrouped];
    });

    const deprecatedProperties = computed<Entry[]>(() => {
        const obj = (typeof props.modelValue === "object" && props.modelValue !== null) ? (props.modelValue as Record<string, any>) : {};
        return props.merge ? [] : sortedProperties.value.filter(([k, v]) => v && isDeprecated(v) && obj[k] !== undefined);
    });

    const hasGroupedProperties = computed<boolean>(() => {
        return groupSections.value.length > 0 || deprecatedProperties.value.length > 0;
    });


    function onInput(value: any) {
        emit("update:modelValue", collapseEmptyValues(value));
    }

    function onObjectInput(propertyName: string, value: any) {
        const currentValue = (typeof props.modelValue === "object" && props.modelValue !== null ? {...(props.modelValue as Record<string, any>)} : {});
        currentValue[propertyName] = value;
        onInput(currentValue);
    }

    function fieldProps(key: string, schema: any) {
        const mv = (typeof props.modelValue === "object" && props.modelValue !== null) ? (props.modelValue as Record<string, any>)[key] : undefined;
        return {
            modelValue: mv,
            "onUpdate:modelValue": (value: any) => onObjectInput(key, value),
            root: props.root,
            fieldKey: key,
            task: props.modelValue,
            schema: schema,
            required: props.schema?.required,
        } as const;
    }
</script>

<style lang="scss">
    .el-form-item__content {
        display: block !important;
        .el-form-item {
            width: 100%;
        }
    }

    .el-popper.singleton-tooltip {
        max-width: 300px !important;
        background: var(--ks-tooltip-background);
    }
</style>

<style scoped lang="scss">
@import "../../styles/code.scss";

.el-form-item {
    width: 100%;
    margin-bottom: 0;
    > :deep(.el-form-item__label) {
        width: 100%;
        display: flex;
        align-items: center;
        padding: 0;
    }
}

.inline-wrapper {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-width: 0;

    .inline-start {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0;
        flex: 1 1 auto;
    }

    .label {
        color: var(--ks-content-primary);
        min-width: 0;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        font-weight: 600;
    }

    .type-tag {
        background-color: var(--ks-tag-background-active);
        color: var(--ks-tag-content);
        font-size: 12px;
        line-height: 20px;
        padding: 0 8px;
        padding-bottom: 2px;
        border-radius: 8px;
        text-transform: capitalize;
    }

    .information-icon {
        color: var(--ks-content-secondary);
        cursor: pointer;
    }
}
</style>
