<template>
    <div class="label-input">
        <KsButton class="label-input-add" :icon="Plus" @click="addItem">
            {{ $t("add label") }}
        </KsButton>

        <div
            class="label-input-row"
            v-for="(label, index) in locals"
            :key="index"
        >
            <KsInput
                class="label-input-field"
                :placeholder="$t('key')"
                :modelValue="(label.key as string | undefined)"
                :disabled="existingRows.has(label)"
                @update:model-value="update(index, $event, 'key')"
            />
            <KsInput
                class="label-input-field"
                :placeholder="$t('value')"
                :modelValue="(label.value as string | undefined)"
                @update:model-value="update(index, $event, 'value')"
            />
            <KsButton
                :icon="Minus"
                :tooltip="$t('remove label')"
                @click="removeItem(index)"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted, watch} from "vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"

    interface Label {
        key: string | null;
        value: string | null;
    }

    const props = defineProps<{
        labels: Label[];
        existingLabels?: Label[];
    }>()

    const emit = defineEmits<{
        (e: "update:labels", value: Label[]): void;
    }>()

    const locals = ref<Label[]>([])
    const existingRows = ref<Set<Label>>(new Set())

    const addItem = () => {
        locals.value.push({key: null, value: null})
        emit("update:labels", locals.value)
    }

    const removeItem = (index: number) => {
        locals.value.splice(index, 1)
        emit("update:labels", locals.value)
    }

    const update = (index: number, value: string | number | undefined, prop: keyof Label) => {
        locals.value[index][prop] = value !== "" && value !== undefined ? String(value) : null
        emit("update:labels", locals.value)
    }

    const syncFromProps = (labels: Label[]) => {
        if (labels.length === 0) {
            locals.value = [{key: null, value: null}]
        } else {
            locals.value = labels
        }
    }

    onMounted(() => {
        if (props.labels.length === 0) {
            addItem()
        } else {
            syncFromProps(props.labels)
            if (locals.value.length === 0) {
                addItem()
            }
        }

        const existingKeys = new Set((props.existingLabels ?? []).map((label) => label.key ?? ""))
        existingRows.value = new Set(
            locals.value
                .filter((label) => label.key != null && existingKeys
                    .has(label.key)),
        )
    })

    watch(
        () => props.labels,
        (labels) => {
            if (labels === locals.value) {
                return
            }
            syncFromProps(labels)
        },
    )
</script>

<style scoped lang="scss">
    .label-input {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .label-input-add {
        align-self: flex-start;
    }

    .label-input-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .label-input-field {
        flex: 1;
        min-width: 0;
    }
</style>
