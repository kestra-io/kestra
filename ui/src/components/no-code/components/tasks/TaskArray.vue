<template>
    <div
        class="task-collection"
        :class="{
            'task-collection--filled': !needWrapper && items.length > 0,
            'task-collection--cards': needWrapper && items.length > 0,
        }"
    >
        <template v-if="needWrapper && canDrillItems">
            <div
                v-for="(element, index) in items"
                :key="'drill-' + index"
                class="task-array-drill"
            >
                <KsDrillRow
                    class="task-array-drill-row"
                    :label="itemLabel(element, index)"
                    :preview="itemPreview(element)"
                    :aria-label="itemLabel(element, index)"
                    data-test="task-array-item-drill"
                    @open="openItem(index)"
                />
                <div class="task-array-drill-actions">
                    <KsIconButton
                        v-if="items.length > 1"
                        :disabled="index === 0"
                        :tooltip="$t('block_editor.move_up')"
                        @click.stop="moveItem(index, 'up')"
                    >
                        <ChevronUp />
                    </KsIconButton>
                    <KsIconButton
                        v-if="items.length > 1"
                        :disabled="index === items.length - 1"
                        :tooltip="$t('block_editor.move_down')"
                        @click.stop="moveItem(index, 'down')"
                    >
                        <ChevronDown />
                    </KsIconButton>
                    <KsIconButton
                        :tooltip="$t('block_editor.delete')"
                        @click.stop="removeItem(index)"
                    >
                        <DeleteOutline />
                    </KsIconButton>
                </div>
            </div>
        </template>

        <template v-else-if="needWrapper">
            <div
                v-for="(element, index) in items"
                :key="'array-' + index"
                class="task-array-item"
            >
                <div class="task-array-item-head">
                    <span class="task-array-item-index">{{ index + 1 }}</span>
                    <div class="task-array-item-actions">
                        <KsIconButton
                            v-if="items.length > 1"
                            placement="bottom"
                            :disabled="index === 0"
                            :tooltip="$t('block_editor.move_up')"
                            @click.stop="moveItem(index, 'up')"
                        >
                            <ChevronUp />
                        </KsIconButton>
                        <KsIconButton
                            v-if="items.length > 1"
                            placement="bottom"
                            :disabled="index === items.length - 1"
                            :tooltip="$t('block_editor.move_down')"
                            @click.stop="moveItem(index, 'down')"
                        >
                            <ChevronDown />
                        </KsIconButton>
                        <KsIconButton
                            placement="bottom"
                            :tooltip="$t('block_editor.delete')"
                            @click.stop="removeItem(index)"
                        >
                            <DeleteOutline />
                        </KsIconButton>
                    </div>
                </div>
                <div class="task-array-item-body">
                    <component
                        :is="componentType"
                        :modelValue="element"
                        :task="modelValue"
                        :root="`${root}[${index}]`"
                        :properties="{}"
                        :schema="props.schema.items"
                        :bare="true"
                        @update:model-value="handleInput($event, index)"
                    />
                </div>
            </div>
        </template>

        <template v-else>
            <KsRow
                v-for="(element, index) in items"
                :key="'array-' + index"
                :gutter="10"
                align="top"
                class="w-100"
            >
                <KsCol :span="2" class="d-flex flex-column justify-content-center reorder" v-if="items.length > 1">
                    <KsIconButton
                        :disabled="index === 0"
                        :tooltip="$t('block_editor.move_up')"
                        @click.prevent.stop="moveItem(index, 'up')"
                    >
                        <ChevronUp />
                    </KsIconButton>
                    <KsIconButton
                        :disabled="index === items.length - 1"
                        :tooltip="$t('block_editor.move_down')"
                        @click.prevent.stop="moveItem(index, 'down')"
                    >
                        <ChevronDown />
                    </KsIconButton>
                </KsCol>
                <KsCol :span="items.length > 1 ? 20 : 22" class="pe-2 array-value-col">
                    <Wrapper merge>
                        <template #tasks>
                            <component
                                :is="componentType"
                                :modelValue="element"
                                :task="modelValue"
                                :root="`${root}[${index}]`"
                                :properties="{}"
                                :schema="props.schema.items"
                                @update:model-value="handleInput($event, index)"
                            />
                        </template>
                    </Wrapper>
                </KsCol>
                <KsCol :span="2" class="delete">
                    <KsIconButton
                        :tooltip="$t('block_editor.delete')"
                        @click.stop="removeItem(index)"
                    >
                        <DeleteOutline />
                    </KsIconButton>
                </KsCol>
            </KsRow>
        </template>

        <Add :to="addTargetName" @add="addItem()" />
    </div>
</template>

<style scoped lang="scss">
@import "../../styles/code.scss";

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}

.array-value-col {
    flex: 1;
    max-width: none;
}

.task-collection--cards {
    gap: var(--ks-spacing-3);
}

.task-array-drill {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
}

.task-array-drill-row {
    flex: 1;
    min-width: 0;
}

.task-array-drill-actions {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    flex: none;
}

.task-array-item {
    border: 1px solid var(--ks-border-subtle);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
    overflow: hidden;
}

.task-array-item-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-1) var(--ks-spacing-2) var(--ks-spacing-1) var(--ks-spacing-3);
    background: var(--ks-bg-elevated);
    border-bottom: 1px solid var(--ks-border-subtle);
}

.task-array-item-index {
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    color: var(--ks-text-secondary);
    font-variant-numeric: tabular-nums;
}

.task-array-item-actions {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
}

.task-array-item-body {
    padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-2);
}
</style>

<script setup lang="ts">
    import {computed, inject, provide, ref, watch} from "vue"

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../utils/icons"

    import {useI18n} from "vue-i18n"
    import Add from "../Add.vue"
    import Wrapper from "./Wrapper.vue"
    import {BLOCK_SCHEMA_PATH_INJECTION_KEY, FIELD_NAV_INJECTION_KEY, SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys"
    import {useBlockComponent} from "./useBlockComponent"
    import {summarizeValue, shouldDrillItem} from "./fieldNesting"

    defineOptions({inheritAttrs: false})

    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref())

    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => {
        return [blockSchemaPath.value, "properties", props.root, "items"].join("/")
    }))

    const emits = defineEmits(["update:modelValue"])
    const props = withDefaults(defineProps<{
        schema?: any;
        modelValue?: (string | number | boolean | undefined)[] | string | number | boolean;
        required?: boolean;
        root?: string;
    }>(), {
        modelValue: undefined,
        schema: () => ({}),
        required: false,
        root: undefined,
    })

    const {getBlockComponent} = useBlockComponent()

    const {t} = useI18n()

    const addTargetName = computed(() =>
        props.root?.split(".").pop()?.replace(/\[\d+\]$/, "") || undefined)

    const fieldNav = inject(FIELD_NAV_INJECTION_KEY, undefined)
    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, ref<Record<string, any>>({}))

    const canDrillItems = computed(() =>
        Boolean(fieldNav) && shouldDrillItem(props.schema?.items, definitions.value),
    )

    function itemLabel(element: any, index: number): string {
        if (element && typeof element === "object" && !Array.isArray(element)) {
            return String(element.id ?? element.name ?? element.type ?? `#${index + 1}`)
        }
        return `#${index + 1}`
    }

    function itemPreview(element: any): string {
        const summary = summarizeValue(element)
        if (summary.kind === "empty") return t("no_code.nav.not_set")
        if (summary.kind === "count") return t("no_code.nav.items", {count: summary.count})
        return summary.text
    }

    function openItem(index: number) {
        fieldNav?.push({
            path: `${props.root}[${index}]`,
            label: itemLabel(items.value[index], index),
            schema: props.schema.items,
        })
    }

    const componentType = computed(() => {
        return getBlockComponent.value?.(props.schema.items, props.root)
    })

    const needWrapper = computed(() => {
        return ![
            "string",
            "number",
            "boolean",
            "expression",
        ].includes(componentType.value.ksTaskName)
    })

    const items = ref<any[]>([])
    const localEdit = ref(false)

    watch(() => props.modelValue, (value) => {
        if (localEdit.value) {
            localEdit.value = false
            return
        }
        items.value = value === undefined && !props.required
            ? []
            : !Array.isArray(value) ? [value] : [...value]
    }, {immediate: true, deep: true})

    function emitItems(value: any) {
        localEdit.value = true
        emits("update:modelValue", value)
    }

    const handleInput = (value: string, index: number) => {
        items.value.splice(index, 1, value)
        emitItems([...items.value])
    }

    const newEmptyValue = computed(() => {
        if (props.schema.items?.type === "string") {
            return ""
        }
        return props.schema.items?.default ?? undefined
    })

    const addItem = () => {
        items.value.push(newEmptyValue.value)
        emitItems([...items.value])
    }

    const removeItem = (index: number) => {
        const next = [...items.value]
        next.splice(index, 1)
        items.value = next
        emitItems(next.length ? next : undefined)
    }

    const moveItem = (index: number, direction: "up" | "down") => {
        const next = [...items.value]
        if (direction === "up" && index > 0) {
            [next[index - 1], next[index]] = [next[index], next[index - 1]]
        } else if (direction === "down" && index < next.length - 1) {
            [next[index + 1], next[index]] = [next[index], next[index + 1]]
        }
        items.value = next
        emitItems(next)
    }
</script>
