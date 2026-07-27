<template>
    <span v-if="props.labels.length" class="d-flex gap-1 labels-container" :class="{wrap}">
        <KsCheckTag
            v-for="(label, index) in visibleLabels"
            :key="index"
            :disabled="readOnly"
            :checked="isChecked(label)"
            @change="updateLabel(label)"
            class="me-0 label"
        >
            {{ labelText(label) }}
        </KsCheckTag>

        <KsTooltip
            v-if="hiddenLabels.length"
        >
            <KsCheckTag class="me-0 label">
                +{{ hiddenLabels.length }}
            </KsCheckTag>
            <template #content>
                <ul class="labels-overflow__list">
                    <li
                        v-for="(label, index) in hiddenLabels"
                        :key="index"
                    >
                        {{ labelText(label) }}
                    </li>
                </ul>
            </template>
        </KsTooltip>
    </span>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"

    import {useRouter, useRoute} from "vue-router"
    import {KsTooltip} from "@kestra-io/design-system"
    const router = useRouter()
    const route = useRoute()

    interface Label {
        key?: string;
        value: string;
        text?: string;
    }

    const props = withDefaults(
        defineProps<{
            labels?: Label[];
            readOnly?: boolean;
            filterType?: "labels" | "metadata" | "type" | "details";
            wrap?: boolean;
            max?: number;
        }>(),
        {
            labels: () => [],
            readOnly: false,
            filterType: "labels",
            wrap: false,
            max: undefined,
        },
    )

    const labelText = (label: Label) => label.text ?? (label.key ? `${label.key}:${label.value}` : label.value)

    const visibleLabels = computed(() =>
        props.max != null ? props.labels.slice(0, props.max) : props.labels,
    )
    const hiddenLabels = computed(() =>
        props.max != null ? props.labels.slice(props.max) : [],
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
    display: inline-flex;
    align-items: center;
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

.labels-overflow__list {
    margin: 0;
    padding-left: var(--ks-spacing-3);
    list-style: disc;

    li + li {
        margin-top: var(--ks-spacing-1);
    }
}
</style>
