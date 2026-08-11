<template>
    <div class="filter-details">
        <div class="active-pairs">
            <div class="section-title">
                {{ $t('filter.active key value pairs') }}
            </div>
            <div v-if="detailPairs.length === 0" class="empty-state">
                {{ $t('none') }}
            </div>
            <div v-else class="pairs-container">
                <KsTag
                    v-for="(pair, index) in detailPairs"
                    :key="index"
                    closable
                    effect="light"
                    @close="removePair(index)"
                    class="detail-tag"
                >
                    <span class="detail-key">{{ pair.key }}:</span><span class="detail-value">{{ pair.value }}</span>
                </KsTag>
            </div>
        </div>

        <div class="add-pair">
            <div class="input-group">
                <label class="input-label">{{ $t('filter.key') }}</label>
                <KsInput
                    v-model="newKey"
                    placeholder="e.g. flowId"
                    @keydown.enter="addPair"
                />
            </div>
            <div class="input-group">
                <label class="input-label">{{ $t('filter.value') }}</label>
                <KsInput
                    v-model="newValue"
                    placeholder="e.g. orchestrator-1234"
                    @keydown.enter="addPair"
                />
            </div>

            <KsButton
                type="default"
                size="small"
                class="add-btn"
                :disabled="!newKey.trim() || !newValue.trim()"
                @click="addPair"
            >
                {{ $t('add') }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"

    const props = withDefaults(defineProps<{
        modelValue: string[];
    }>(), {
    })

    const emits = defineEmits<{
        "update:modelValue": [value: string[]];
    }>()

    const newKey = ref("")
    const newValue = ref("")
    const detailPairs = ref<Array<{ key: string; value: string }>>([])

    // For Auditlogs Details KV pairs parsing and serialization
    const parseDetailPairs = (values: string[]) =>
        values?.map(value => {
            const [key, ...valueParts] = value?.split(":") ?? []
            return {key: key ?? "", value: valueParts?.join(":") ?? ""}
        }).filter(pair => pair.key && pair.value) ?? []

    const serializeDetailPairs = (pairs: typeof detailPairs.value) =>
        pairs.map(pair => `${pair.key}:${pair.value}`)

    const addPair = () => {
        const key = newKey.value.trim(), value = newValue.value.trim()
        if (!key || !value) return

        const existingIndex = detailPairs.value.findIndex(pair => pair.key === key)
        if (existingIndex !== -1) {
            detailPairs.value[existingIndex].value = value
        } else {
            detailPairs.value.push({key, value})
        }

        emits("update:modelValue", serializeDetailPairs(detailPairs.value))
        newKey.value = newValue.value = ""
    }

    const removePair = (index: number) => {
        detailPairs.value.splice(index, 1)
        emits("update:modelValue", serializeDetailPairs(detailPairs.value))
    }

    watch(() => props.modelValue, (val) => {
        detailPairs.value = val ? parseDetailPairs(val) : []
    }, {immediate: true})
</script>

<style lang="scss" scoped>
.active-pairs {
    padding: 1rem;
    border-bottom: 1px solid var(--ks-border-default);

    .section-title {
        color: var(--ks-text-dim);
        font-size: var(--ks-font-size-xs);
        font-weight: 500;
        margin-bottom: 8px;
    }

    .empty-state {
        color: var(--ks-text-dim);
        font-size: var(--ks-font-size-sm);
        font-style: italic;
    }

    .pairs-container {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;

        .detail-tag {
            display: inline-flex;
            align-items: center;
            max-width: 270px;
            padding: 3px 6px;
            border-radius: 16px;
            border: 1px solid var(--ks-border-info);
            background-color: var(--ks-bg-info);
            color: var(--ks-text-info);
            font-size: var(--ks-font-size-xs);

            :deep(.kel-tag__content) {
                display: flex;
                align-items: center;
                overflow: hidden;
                white-space: nowrap;
                text-overflow: ellipsis;
            }
        }

        :deep(.kel-tag__close) {
            color: var(--ks-text-info);
            background: transparent;
        }

        .detail-key {
            flex-shrink: 0;
            font-weight: 600;
            margin-right: 5px;
        }

        .detail-value {
            flex: 1;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }
}

.add-pair {
    padding: 1rem;

    .input-group {
        margin-bottom: 12px;

        &:last-child {
            margin-bottom: 0;
        }

        .input-label {
            display: block;
            margin-bottom: 6px;
            font-size: var(--ks-font-size-xs);
            font-weight: 500;
            color: var(--ks-text-secondary);
        }
    }

    .add-btn {
        width: 100%;
        margin-top: 12px;
    }
}

:deep(.kel-input__inner) {
    font-size: var(--ks-font-size-sm);

    &::placeholder {
        color: var(--ks-text-dim);
    }
}
</style>