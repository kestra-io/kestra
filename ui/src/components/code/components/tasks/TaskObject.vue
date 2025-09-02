
<template>
    <el-form label-position="top" class="w-100">
        <template v-if="sortedProperties">
            <template v-for="[fieldKey, fieldSchema] in protectedRequiredProperties" :key="fieldKey">
                <Wrapper :merge>
                    <template #tasks>
                        <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                    </template>
                </Wrapper>
            </template>

            <el-collapse v-model="activeNames" v-if="requiredProperties.length && (optionalProperties?.length || deprecatedProperties?.length || connectionProperties?.length)" class="collapse">
                <el-collapse-item name="connection" v-if="connectionProperties?.length" :title="t('no_code.sections.connection')">
                    <template v-for="[fieldKey, fieldSchema] in connectionProperties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>
                <el-collapse-item name="optional" v-if="optionalProperties?.length" :title="t('no_code.sections.optional')">
                    <template v-for="[fieldKey, fieldSchema] in optionalProperties" :key="fieldKey">
                        <Wrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </Wrapper>
                    </template>
                </el-collapse-item>

                <el-collapse-item name="deprecated" v-if="deprecatedProperties?.length" :title="t('no_code.sections.deprecated')">
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
            <task-dict
                :model-value="modelValue"
                :task="task"
                @update:model-value="
                    (value) => $emit('update:modelValue', value)
                "
                :root="root"
                :schema="schema ?? {}"
                :required="required"
                :definitions="definitions"
            />
        </template>
    </el-form>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import TaskDict from "./TaskDict.vue";
    import Wrapper from "./Wrapper.vue";
    import TaskObjectField from "./TaskObjectField.vue";
    import {collapseEmptyValues} from "./MixinTask";

    defineOptions({
        name: "TaskObject",
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
        definitions?: any;
        // passed-through by parent in some contexts
        task?: any;
        root?: string;
    }>();

    const emit = defineEmits<{
        (e: "update:modelValue", value: Model): void;
    }>();

    const {t} = useI18n();

    const activeNames = ref<string[]>([]);

    const FIRST_FIELDS = ["id", "forced", "on", "type"] as const;

    type Entry = [string, any];

    function sortProperties(properties: Entry[], required?: string[]): Entry[] {
        if (!properties?.length) return [];
        return properties.slice().sort((a, b) => {
            if (FIRST_FIELDS.includes(a[0] as any)) return -1;
            if (FIRST_FIELDS.includes(b[0] as any)) return 1;

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

    const filteredProperties = computed<Entry[]>(() => {
        const propertiesProc = (props.properties ?? props.schema?.properties);
        return propertiesProc
            ? (Object.entries(propertiesProc) as Entry[]).filter(([key, value]) => key !== "type" && !Array.isArray(value))
            : [];
    });

    const sortedProperties = computed<Entry[]>(() => sortProperties(filteredProperties.value, props.schema?.required));

    const isRequired = (key: string) => Boolean(props.schema?.required?.includes(key));

    const requiredProperties = computed<Entry[]>(() => {
        return props.merge ? sortedProperties.value : sortedProperties.value.filter(([p, v]) => v && isRequired(p));
    });

    const protectedRequiredProperties = computed<Entry[]>(() => {
        return requiredProperties.value.length ? requiredProperties.value : sortedProperties.value;
    });

    const optionalProperties = computed<Entry[]>(() => {
        return props.merge ? [] : sortedProperties.value.filter(([p, v]) => v && !isRequired(p) && !isDeprecated(v) && v.$group !== "connection");
    });

    const deprecatedProperties = computed<Entry[]>(() => {
        const obj = (typeof props.modelValue === "object" && props.modelValue !== null) ? (props.modelValue as Record<string, any>) : {};
        return props.merge ? [] : sortedProperties.value.filter(([k, v]) => v && isDeprecated(v) && obj[k] !== undefined);
    });

    const connectionProperties = computed<Entry[]>(() => {
        return props.merge ? [] : sortedProperties.value.filter(([p, v]) => v && v.$group === "connection" && !isRequired(p));
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
            definitions: props.definitions,
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

<style lang="scss" scoped>
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
