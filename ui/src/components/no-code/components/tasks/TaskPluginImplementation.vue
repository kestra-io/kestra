<template>
    <div class="plugin-implementation" data-test="plugin-implementation">
        <div class="plugin-implementation-label">
            <span class="plugin-implementation-name" data-test="plugin-implementation-name">{{ label }}</span>
            <span class="plugin-implementation-key">{{ fieldKey }}</span>
            <KsTag v-if="required" size="xs" type="error" :label="$t('plugins.required')" />
            <KsTag
                size="xs"
                data-test="plugin-implementation-count"
                :label="branches.length > 1
                    ? $t('block_editor.plugin_implementation.count_available', {count: branches.length})
                    : $t('block_editor.plugin_implementation.exactly_one')"
            />
        </div>

        <template v-if="isArray">
            <p v-if="items.length" class="plugin-implementation-meta">
                {{ $t("block_editor.plugin_implementation.items_set", {count: items.length}) }}
            </p>
            <div class="plugin-implementation-items">
                <ImplementationCard
                    v-for="(item, index) in items"
                    :key="index"
                    :modelValue="item"
                    :branches="branches"
                    :canChange="branches.length > 1"
                    :label="label"
                    :removeLabel="itemRemoveLabel(item)"
                    :root="`${root}[${index}]`"
                    @update:modelValue="(value) => updateItem(index, value)"
                    @remove="removeItem(index)"
                    @change="openPicker(index, typeOf(item))"
                />
            </div>
            <BlockEmptyDrop
                variant="inline"
                :label="addLabel"
                :required="requiredMissing"
                :aria-describedby="requiredMissing ? requiredMessageId : undefined"
                data-test="plugin-implementation-add"
                @add="openPicker(undefined, undefined)"
            />
        </template>
        <template v-else-if="hasValue">
            <ImplementationCard
                :modelValue="modelValue as Record<string, unknown>"
                :branches="branches"
                :canChange="branches.length > 1"
                :label="label"
                :removeLabel="singleRemoveLabel"
                :root="root"
                @update:modelValue="onSingleUpdate"
                @remove="onRemove"
                @change="openPicker(undefined, typeOf(modelValue as Record<string, unknown>))"
            />
        </template>
        <template v-else>
            <BlockEmptyDrop
                variant="inline"
                :label="addLabel"
                :required="requiredMissing"
                :aria-describedby="requiredMissing ? requiredMessageId : undefined"
                data-test="plugin-implementation-add"
                @add="openPicker(undefined, undefined)"
            />
        </template>

        <span v-if="requiredMissing" :id="requiredMessageId" class="required-missing" data-test="field-required-missing">
            <AlertCircleOutline class="required-missing-icon" />
            {{ $t("block_editor.required_missing") }}
        </span>

        <ImplementationPicker
            v-if="branches.length > 1"
            v-model="pickerVisible"
            :title="pickerTitle"
            :branches="branches"
            :current="pickerCurrentType"
            @select="onPickerSelect"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref, useId} from "vue"
    import {useI18n} from "vue-i18n"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import BlockEmptyDrop from "../../blocks/BlockEmptyDrop.vue"
    import ImplementationCard from "./ImplementationCard.vue"
    import ImplementationPicker from "./ImplementationPicker.vue"
    import type {Schema} from "./getTaskComponent"
    import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys"
    import {
        findBranchByType,
        getImplementationBranches,
        humanizeClassName,
        resolveImplementationLabel,
        simpleClassName,
        type ImplementationBranch,
    } from "./discriminatedUnion"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        schema: Schema
        required?: boolean
        root?: string
    }>()

    type Implementation = Record<string, unknown>

    const modelValue = defineModel<Implementation | Implementation[] | undefined>()

    const {t} = useI18n()

    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, computed<Record<string, Schema>>(() => ({})))

    const fieldKey = computed(() => props.root?.split(".").pop() ?? "")

    const branches = computed<ImplementationBranch[]>(() => getImplementationBranches(props.schema, definitions.value) ?? [])

    const label = computed(() => resolveImplementationLabel(branches.value, fieldKey.value))

    const isArray = computed(() => props.schema?.type === "array")

    const items = computed<Implementation[]>(() => Array.isArray(modelValue.value) ? modelValue.value : [])

    const hasValue = computed(() => !isArray.value && modelValue.value !== undefined && modelValue.value !== null)

    const addLabel = computed(() => t("block_editor.inline_add", {label: label.value}))

    const requiredMessageId = useId()

    const requiredMissing = computed(() => {
        if (!props.required) return false
        return isArray.value ? items.value.length === 0 : !hasValue.value
    })

    function typeOf(item: Implementation | undefined): string | undefined {
        const type = item?.type
        return typeof type === "string" ? type : undefined
    }

    function branchName(type: string | undefined): string {
        const branch = type ? findBranchByType(branches.value, type) : undefined
        return branch ? humanizeClassName(simpleClassName(branch.ref)) : (type ?? "")
    }

    const singleRemoveLabel = computed(() => t("block_editor.plugin_implementation.remove", {label: label.value}))

    function itemRemoveLabel(item: Implementation): string {
        return t("block_editor.plugin_implementation.remove_from", {name: branchName(typeOf(item)), field: label.value})
    }

    const pickerVisible = ref(false)
    const pickerTargetIndex = ref<number | undefined>(undefined)
    const pickerCurrentType = ref<string | undefined>(undefined)

    const pickerTitle = computed(() => t("block_editor.plugin_implementation.choose_title", {label: label.value}))

    function commit(value: Implementation | undefined, index: number | undefined) {
        if (isArray.value) {
            const next = [...items.value]
            if (index === undefined) {
                next.push(value as Implementation)
            } else if (value === undefined) {
                next.splice(index, 1)
            } else {
                next.splice(index, 1, value)
            }
            modelValue.value = next.length ? next : undefined
            return
        }
        modelValue.value = value
    }

    function openPicker(index: number | undefined, currentType: string | undefined) {
        if (branches.value.length <= 1) {
            if (branches.value.length === 1) {
                commit({type: branches.value[0].ref}, index)
            }
            return
        }
        pickerTargetIndex.value = index
        pickerCurrentType.value = currentType
        pickerVisible.value = true
    }

    function onPickerSelect(branch: ImplementationBranch) {
        commit({type: branch.ref}, pickerTargetIndex.value)
        pickerVisible.value = false
    }

    function updateItem(index: number, value: Implementation | undefined) {
        commit(value, index)
    }

    function removeItem(index: number) {
        commit(undefined, index)
    }

    function onSingleUpdate(value: Implementation | undefined) {
        modelValue.value = value
    }

    function onRemove() {
        modelValue.value = undefined
    }
</script>

<style scoped lang="scss">
.plugin-implementation {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
    margin: var(--ks-spacing-1) 0 var(--ks-spacing-2);
}

.plugin-implementation-label {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
}

.plugin-implementation-name {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    color: var(--ks-text-primary);
}

.plugin-implementation-key {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-muted);
}

.plugin-implementation-meta {
    margin: 0;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
}

.plugin-implementation-items {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
}

.required-missing {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-error);
}

.required-missing-icon {
    display: inline-flex;
    font-size: var(--ks-font-size-sm);
}
</style>
