<template>
    <span v-if="props.labels.length" class="d-flex gap-1 labels-container" :class="{wrap}">
        <KsCheckTag
            v-for="(label, index) in props.labels"
            :key="index"
            :disabled="readOnly"
            :checked="isChecked(label)"
            @change="updateLabel(label)"
            class="me-0 el-tag label"
        >
            <template v-if="!label.key">{{ label.value }}</template>
            <template v-else>{{ label.key }}:{{ label.value }}</template>
        </KsCheckTag>
    </span>
</template>

<script setup lang="ts">
    import {watch} from "vue"

    import {useRouter, useRoute} from "vue-router"
    const router = useRouter()
    const route = useRoute()

    interface Label {
        key?: string;
        value: string;
    }

    const props = withDefaults(
        defineProps<{
            labels?: Label[];
            readOnly?: boolean;
            filterType?: "labels" | "metadata" | "type" | "details";
            wrap?: boolean;
        }>(),
        {
            labels: () => [],
            readOnly: false,
            filterType: "labels",
            wrap: false,
        },
    )

    import {decodeSearchParams} from "@kestra-io/design-system"
    let query: any[] = []
    watch(
        () => route.query,
        (q) => (query = decodeSearchParams(q)),
        {immediate: true},
    )

    const isChecked = (label: Label) => {
        return query.some((l) => {
            if (props.filterType === "type") {
                return l.field === props.filterType && l.operation === "EQUALS" && typeof l.value === "string" && l.value === label.value
            }

            if (typeof l?.value !== "string") return false

            const [key, value] = l.value.split(":")
            return l.field === props.filterType && l.operation === "EQUALS" && key === label.key && value === label.value
        })
    }

    const updateLabel = (label: Label) => {
        const getKey = (key?: string) => (props.filterType === "type"
            ? `filters[${props.filterType}][EQUALS]`
            : `filters[${props.filterType}][EQUALS][${key}]`)

        if (isChecked(label)) {
            const replacementQuery = {...route.query} as Record<string, any>
            delete replacementQuery[props.filterType === "type" ? getKey() : getKey(label.key)]
            replacementQuery.page = "1"
            router.replace({query: replacementQuery})
        } else {
            const newQuery = {...route.query, page: "1"} as Record<string, any>
            if (props.filterType === "type") {
                newQuery[getKey()] = label.value
            } else {
                newQuery[getKey(label.key)] = label.value
            }
            router.replace({query: newQuery})
        }
    }
</script>

<style scoped lang="scss">
.label.kel-check-tag {
    --ks-bg-tag: #7b7b7e45;
;
    --ks-bg-tag-active: #414557;
    --label-text-active: #ffffff;

    html.dark & {
        --ks-bg-tag: #FFFFFF1A;
;
        --ks-bg-tag-active: #F2F2F2;
        --label-text-active: var(--ks-text-primary);
    }

    background-color: var(--ks-bg-tag);
    color: var(--ks-text-primary);
    font-size: var(--ks-font-size-xs);
    padding: 4px 6px;
    border-radius: 6px;
    font-weight: 400;
    white-space: nowrap;
}

.labels-container {
    overflow: hidden;
    flex-wrap: nowrap;
    min-width: 0;

    &.wrap {
        flex-wrap: wrap;
        overflow: visible;
    }
}

.label.kel-check-tag.is-checked {
    background-color: var(--ks-bg-tag-active);
    color: var(--ks-black);
    font-weight: var( --ks-font-weight-medium);

    html.light & {
        color: var(--label-text-active);
    }
}
</style>
