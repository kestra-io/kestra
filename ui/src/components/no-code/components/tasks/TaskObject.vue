<template>
    <el-form labelPosition="top" class="w-100">
        <template v-if="sortedProperties">
            <template v-for="[fieldKey, fieldSchema] in protectedRequiredProperties" :key="fieldKey">
                <Wrapper :merge>
                    <template #tasks>
                        <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                    </template>
                </Wrapper>
            </template>

            <el-collapse v-model="activeNames" v-if="requiredProperties.length && (optionalProperties?.length || deprecatedProperties?.length || connectionProperties?.length)" class="collapse">
                <el-collapse-item name="connection" v-if="connectionProperties?.length" :title="$t('no_code.sections.connection')">
                    <template v-for="[fieldKey, fieldSchema] in connectionProperties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>
                <el-collapse-item name="optional" v-if="optionalProperties?.length" :title="$t('no_code.sections.optional')">
                    <template v-for="[fieldKey, fieldSchema] in optionalProperties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>
                <el-collapse-item name="general" v-if="generalProperties?.length" :title="$t('no_code.sections.general')">
                    <template v-for="[fieldKey, fieldSchema] in generalProperties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>
                <el-collapse-item name="deprecated" v-if="deprecatedProperties?.length" :title="$t('no_code.sections.deprecated')">
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
    import TaskDict from "./TaskDict.vue";
    import Wrapper from "./Wrapper.vue";
    import TaskObjectField from "./TaskObjectField.vue";
    import {collapseEmptyValues} from "./MixinTask";
    import {DATA_TYPES_MAP_INJECTION_KEY} from "../../injectionKeys";

    defineOptions({
        inheritAttrs: false,
    });

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
    }>();

    const emit = defineEmits<{
        (e: "update:modelValue", value: Model): void;
    }>();

    const activeNames = ref<string[]>([]);

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

    const filteredProperties = computed<Entry[]>(() => {
        const propertiesProc = (props.properties ?? props.schema?.properties);
        const isOutputsContext = props.root?.startsWith("outputs[") || false;
        return propertiesProc
            ? (Object.entries(propertiesProc) as Entry[]).filter(([key, value]) => {
                // Allow "type" field for outputs context, filter it out for other contexts
                const shouldFilterType = key === "type" && !isOutputsContext;
                return !shouldFilterType && !Array.isArray(value);
            })
            : [];
    });

    const sortedProperties = computed<Entry[]>(() => sortProperties(filteredProperties.value, props.schema?.required));

    const isRequired = (key: string) => Boolean(props.schema?.required?.includes(key));

    const dataTypesMap = inject(DATA_TYPES_MAP_INJECTION_KEY, ref<Record<string, string[] | undefined>>({}));

    const requiredProperties = computed<Entry[]>(() => {
        const properties =  props.merge ? sortedProperties.value : sortedProperties.value.filter(([p, v]) => v && isRequired(p));
        const dataTypes = dataTypesMap.value[props.root ?? ""]
        if(dataTypes){
            properties.unshift(["type", {
                type: "string",
                enum: dataTypes,
                $required: true,
            }]);
        }
        return properties;
    });

    const protectedRequiredProperties = computed<Entry[]>(() => {
        return requiredProperties.value.length ? requiredProperties.value : sortedProperties.value;
    });
    
    const connectionProperties = computed<Entry[]>(() => {
        return props.merge ? [] : sortedProperties.value.filter(([p, v]) => v && !isRequired(p) && isPartOfGroup(v, ["connection"]));
    });

    const optionalProperties = computed<Entry[]>(() => {
        return props.merge ? [] : sortedProperties.value.filter(([p, v]) => v && !isRequired(p) && !isDeprecated(v) && !isPartOfGroup(v, ["core","connection"]));
    });

    const generalProperties = computed<Entry[]>(() => {
        return props.merge ? [] : sortedProperties.value.filter(([p, v]) => v && !isRequired(p) && !isDeprecated(v) && isPartOfGroup(v, ["core"]));
    });

    const deprecatedProperties = computed<Entry[]>(() => {
        const obj = (typeof props.modelValue === "object" && props.modelValue !== null) ? (props.modelValue as Record<string, any>) : {};
        return props.merge ? [] : sortedProperties.value.filter(([k, v]) => v && isDeprecated(v) && obj[k] !== undefined);
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
