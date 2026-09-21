<template>
    <div class="table-input">
        <KsTable v-if="rows.length" :data="rows" size="small">
            <KsTableColumn v-for="column in columns" :key="column.id" :label="columnLabel(column)">
                <template #default="{$index}">
                    <div class="cell">
                        <KsInputNumber
                            v-if="column.type === 'INT' || column.type === 'FLOAT'"
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as number"
                            @update:modelValue="setCell($index, column, $event)"
                            :min="column.min"
                            :max="column.max ?? Infinity"
                            :step="column.type === 'INT' ? 1 : 0.001"
                            controlsPosition="right"
                        />
                        <KsSwitch
                            v-else-if="column.type === 'BOOL'"
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as boolean"
                            @update:modelValue="setCell($index, column, $event)"
                        />
                        <KsSelect
                            v-else-if="column.type === 'SELECT' || column.type === 'MULTISELECT'"
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as string"
                            @update:modelValue="setCell($index, column, $event)"
                            :multiple="column.type === 'MULTISELECT'"
                            :allowCreate="column.allowCustomValue"
                            filterable
                            clearable
                        >
                            <KsOption
                                v-for="option in options(column)"
                                :key="option.value"
                                :label="option.label"
                                :value="option.value"
                            />
                        </KsSelect>
                        <KsDatePicker
                            v-else-if="column.type === 'DATE' || column.type === 'DATETIME'"
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as string"
                            @update:modelValue="setCell($index, column, $event)"
                            :type="column.type === 'DATE' ? 'date' : 'datetime'"
                            :valueFormat="column.type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss[Z]'"
                        />
                        <KsTimePicker
                            v-else-if="column.type === 'TIME'"
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as string"
                            @update:modelValue="setCell($index, column, $event)"
                            valueFormat="HH:mm:ss"
                        />
                        <KsDurationPicker
                            v-else-if="column.type === 'DURATION'"
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as string"
                            @update:modelValue="setCell($index, column, $event)"
                        />
                        <KsInput
                            v-else
                            :data-test="`table-cell-${input.id}-${$index}-${column.id}`"
                            :modelValue="rows[$index][column.id] as string"
                            @update:modelValue="setCell($index, column, $event)"
                        />
                        <KsText v-if="cellError($index, column.id)" type="danger" size="small" class="cell-error">
                            {{ cellError($index, column.id) }}
                        </KsText>
                    </div>
                </template>
            </KsTableColumn>
            <KsTableColumn width="56" align="center">
                <template #default="{$index}">
                    <KsIconButton
                        :tooltip="$t('remove this item')"
                        :data-test="`table-row-remove-${input.id}-${$index}`"
                        :disabled="rows.length <= minRows"
                        @click="removeRow($index)"
                    >
                        <DeleteOutline />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsTable>
        <KsButton
            class="add-row"
            :icon="Plus"
            :disabled="maxRows !== undefined && rows.length >= maxRows"
            :data-test="`table-row-add-${input.id}`"
            @click="addRow"
        >
            {{ $t('add_new_item') }}
        </KsButton>
    </div>
</template>

<script setup lang="ts">
    import {computed, markRaw, ref, watch, type Component} from "vue"
    import DeleteOutlineIcon from "vue-material-design-icons/DeleteOutline.vue"
    import PlusIcon from "vue-material-design-icons/Plus.vue"
    import type {InputError, InputMetaData, ValueOptionLike} from "../../stores/executions"

    type Row = Record<string, unknown>

    const props = defineProps<{
        input: InputMetaData;
        errors?: InputError[];
    }>()

    const modelValue = defineModel<string | undefined>()

    const DeleteOutline = markRaw(DeleteOutlineIcon) as Component
    const Plus = markRaw(PlusIcon) as Component

    const columns = computed<InputMetaData[]>(() => props.input.columns ?? [])
    const minRows = computed(() => props.input.rows?.min ?? 0)
    const maxRows = computed(() => props.input.rows?.max)

    function parse(value: string | undefined): Row[] {
        if (!value) return []
        try {
            const parsed = JSON.parse(value)
            return Array.isArray(parsed) ? parsed.filter((row): row is Row => row !== null && typeof row === "object") : []
        } catch {
            return []
        }
    }

    function emptyRow(): Row {
        return Object.fromEntries(columns.value.map((column) => [column.id, undefined]))
    }

    // `rows.min` is what the grid opens on, so the user is not asked to add the row the flow requires.
    function withMinRows(value: Row[]): Row[] {
        while (value.length < minRows.value) {
            value.push(emptyRow())
        }
        return value
    }

    const rows = ref<Row[]>(withMinRows(parse(modelValue.value)))

    // The JSON this component last wrote, so an echo of its own value (the validate round-trip writes
    // the model back) does not rebuild the rows and drop the cell the user is typing in.
    let emitted = modelValue.value

    watch(modelValue, (value) => {
        if (value === emitted) return
        rows.value = withMinRows(parse(value))
    })

    function commit(): void {
        emitted = JSON.stringify(rows.value)
        modelValue.value = emitted
    }

    function setCell(index: number, column: InputMetaData, value: unknown): void {
        rows.value[index][column.id] = value
        commit()
    }

    function addRow(): void {
        rows.value.push(emptyRow())
        commit()
    }

    function removeRow(index: number): void {
        rows.value.splice(index, 1)
        commit()
    }

    function columnLabel(column: InputMetaData): string {
        return `${column.displayName || column.id} · ${column.type}`
    }

    function options(column: InputMetaData): {label: string; value: string}[] {
        return ((column.values ?? column.options) ?? []).map((option: ValueOptionLike) =>
            typeof option === "string" ? {label: option, value: option} : option,
        )
    }

    // `disks[2].size_gb` -> `2.size_gb`, keyed that way so a cell lookup is a map hit. The input id is
    // stripped by length rather than by splitting on `.`, since an input id may itself contain dots.
    const cellErrors = computed<Record<string, string>>(() => {
        const result: Record<string, string> = {}
        for (const error of props.errors ?? []) {
            if (!error.path?.startsWith(props.input.id)) continue
            const match = /^\[(\d+)]\.(.+)$/.exec(error.path.slice(props.input.id.length))
            if (!match) continue
            const cause = error.message.split("Cause: ").pop() ?? error.message
            result[`${match[1]}.${match[2]}`] = cause
        }
        return result
    })

    function cellError(index: number, columnId: string): string | undefined {
        return cellErrors.value[`${index}.${columnId}`]
    }
</script>

<style lang="scss" scoped>
    .table-input {
        width: 100%;
    }

    .cell {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .cell-error {
        text-align: left;
    }

    .add-row {
        margin-top: var(--ks-spacing-2);
    }
</style>
