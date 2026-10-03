<template>
    <div class="table-input">
        <div
            v-if="rows.length"
            class="table-input-grid"
            role="group"
            :aria-label="input.displayName || input.id"
            :style="{'--table-input-number-of-columns': columns.length}"
        >
            <div class="table-input-head">
                <TableInputColumnLabel v-for="column in columns" :key="column.id" v-bind="column" />
                <span />
            </div>

            <div class="table-input-body">
                <template v-for="(row, index) in rows" :key="index">
                    <KsFormItem v-for="column in columns" :key="`${index}-${column.id}`" class="table-input-field" :error="cellError(index, column.id)" :showMessage="false">
                        <KsInputNumber
                            v-if="column.type === 'INT' || column.type === 'FLOAT'"
                            v-bind="cellAttrs(column, index)"
                            :modelValue="row[column.id] as number"
                            @update:modelValue="setCell(index, column, $event)"
                            :min="column.min"
                            :max="column.max ?? Infinity"
                            :step="column.type === 'INT' ? 1 : 0.001"
                            controlsPosition="right"
                        />
                        <KsSwitch
                            v-else-if="column.type === 'BOOL'"
                            v-bind="cellAttrs(column, index)"
                            :modelValue="row[column.id] as boolean"
                            @update:modelValue="setCell(index, column, $event)"
                        />
                        <KsSelect
                            v-else-if="column.type === 'SELECT' || column.type === 'MULTISELECT'"
                            v-bind="cellAttrs(column, index)"
                            :modelValue="row[column.id] as string"
                            @update:modelValue="setCell(index, column, $event)"
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
                            v-bind="cellAttrs(column, index)"
                            :modelValue="row[column.id] as string"
                            @update:modelValue="setCell(index, column, $event)"
                            :type="column.type === 'DATE' ? 'date' : 'datetime'"
                            :valueFormat="column.type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss[Z]'"
                        />
                        <KsTimePicker
                            v-else-if="column.type === 'TIME'"
                            v-bind="cellAttrs(column, index)"
                            :modelValue="row[column.id] as string"
                            @update:modelValue="setCell(index, column, $event)"
                            valueFormat="HH:mm:ss"
                        />
                        <KsInput
                            v-else
                            v-bind="cellAttrs(column, index)"
                            :modelValue="row[column.id] as string"
                            @update:modelValue="setCell(index, column, $event)"
                        />
                        <KsText v-if="cellError(index, column.id)" :id="cellErrorId(column, index)" type="danger" size="small" class="cell-error">
                            {{ cellError(index, column.id) }}
                        </KsText>
                    </KsFormItem>
                    <div class="table-input-action">
                        <KsIconButton
                            :tooltip="$t('remove this item')"
                            :data-test="`table-row-remove-${input.id}-${index}`"
                            :disabled="rows.length <= minRows"
                            @click="removeRow(index)"
                        >
                            <DeleteOutline />
                        </KsIconButton>
                    </div>
                </template>
            </div>
            <BlockEmptyDrop
                class="add-row"
                variant="empty"
                :disabled="maxRows !== undefined && rows.length >= maxRows"
                :dataTest="`table-row-add-${input.id}`"
                @add="addRow"
            >
                {{ $t('table_input.add_row') }}
            </BlockEmptyDrop>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, markRaw, ref, watch, type Component} from "vue"
    import DeleteOutlineIcon from "vue-material-design-icons/DeleteOutline.vue"
    import BlockEmptyDrop from "../no-code/blocks/BlockEmptyDrop.vue"
    import type {InputError, InputMetaData, ValueOptionLike} from "../../stores/executions"
    import TableInputColumnLabel from "./TableInputColumnLabel.vue"

    type Row = Record<string, unknown>

    const props = defineProps<{
        input: InputMetaData;
        errors?: InputError[];
    }>()

    const modelValue = defineModel<string | undefined>()

    const DeleteOutline = markRaw(DeleteOutlineIcon) as Component

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
        return Object.fromEntries(columns.value.map((column) => [column.id, null]))
    }

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

    function cellErrorId(column: InputMetaData, index: number): string {
        return `${props.input.id}-${index}-${column.id}-error`
    }

    function cellAttrs(column: InputMetaData, index: number): Record<string, string> {
        const error = cellError(index, column.id)
        return {
            "data-test": `table-cell-${props.input.id}-${index}-${column.id}`,
            "aria-label": column.displayName || column.id,
            ...(error ? {"aria-invalid": "true", "aria-describedby": cellErrorId(column, index)} : {}),
        }
    }



    function options(column: InputMetaData): {label: string; value: string}[] {
        return ((column.values ?? column.options) ?? []).map((option: ValueOptionLike) =>
            typeof option === "string" ? {label: option, value: option} : option,
        )
    }

    // The input id is stripped by length rather than by splitting on `.`, since an id may contain dots.
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

    /* The column tracks are declared once here; every rowgroup and row below subgrids onto them. */
    .table-input-grid {
        display: grid;
        grid-template-columns: repeat(var(--table-input-number-of-columns), minmax(0, 1fr)) auto;
        column-gap: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        overflow: hidden;
        &:focus-within {
            border-color: var(--ks-border-focus);
        }
    }

    /* The inline padding has to match on both, or their subgrid tracks inset by different amounts
       and the header stops lining up with the cells. The block padding is free to differ. */
    .table-input-head,
    .table-input-body {
        display: grid;
        grid-column: 1 / -1;
        grid-template-columns: subgrid;
        padding-inline: var(--ks-spacing-3);
        row-gap: var(--ks-spacing-2);
        align-items: start;
    }

    .table-input-head {
        background: var(--ks-bg-base);
        border-bottom: 1px solid var(--ks-border-default);
    }

    .table-input-body {
        padding-block: var(--ks-spacing-2);
    }

    /* A column shares whatever width there is rather than forcing the form to scroll sideways. */
    .table-input-field {
        min-width: 0;
        margin-bottom: 0;
    }

    .cell-error {
        display: block;
        margin-top: var(--ks-spacing-1);
        line-height: 1.3;
        overflow-wrap: anywhere;
    }

    .table-input-action {
        display: flex;
        align-items: flex-start;
        padding-block: var(--ks-spacing-1);
        justify-content: center;
        /* The height of a control, which no token carries. */
        min-height: 2rem;
    }

    .add-row {
        grid-column: 1 / calc(var(--table-input-number-of-columns) + 2);
        margin: var(--ks-spacing-3);
        margin-top: var(--ks-spacing-1);
    }
</style>
