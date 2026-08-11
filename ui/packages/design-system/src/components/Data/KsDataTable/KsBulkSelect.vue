<template>
    <div class="ks-bulk-select">
        <KsCheckbox
            :modelValue="selectionCount > 0"
            @change="toggle"
            :indeterminate="partialCheck"
        >
            <span v-html="t('selected', {count: selectAll && total !== undefined ? total : selectionCount})" />
        </KsCheckbox>
        <KsButtonGroup>
            <KsButton
                :type="selectAll ? 'primary' : 'default'"
                @click="toggleAll"
                v-if="total !== undefined && selectionCount < total"
            >
                <span v-html="t('all', {count: total!})" />
            </KsButton>
            <slot />
        </KsButtonGroup>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import locale from "./KsBulkSelect.locale.ts"

    const {t} = useI18n({
        useScope: "local",
        inheritLocale: true,
        messages: locale,
    })

    const props = withDefaults(defineProps<{
        total?: number
        selectionCount: number
        selectAll: boolean
    }>(), {
        total: undefined,
    })

    const emit = defineEmits<{
        "toggle-all": []
        unselect: []
    }>()

    const partialCheck = computed(() => {
        return !props.selectAll && (props.total === undefined || props.selectionCount < (props.total ?? 0))
    })

    function toggle(value: boolean) {
        if (!value) {
            emit("unselect")
        }
    }

    function toggleAll() {
        emit("toggle-all")
    }
</script>

<style lang="scss">
    .ks-bulk-select {
        height: 100%;
        display: flex;
        align-items: center;

        .kel-checkbox {
            height: 100%;
        }

        .kel-button-group {
            display: flex;
        }

        > * {
            padding: 0 8px;
        }
    }
</style>
