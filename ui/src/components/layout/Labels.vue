<template>
    <span v-if="props.labels.length" class="d-flex flex-wrap gap-1">
        <el-check-tag
            v-for="(label, index) in props.labels"
            :key="index"
            :disabled="readOnly"
            :checked="isChecked(label)"
            @change="updateLabel(label)"
            class="me-0 el-tag label"
        >
            <template v-if="!label.key">{{ label.value }}</template>
            <template v-else>{{ label.key }}:{{ label.value }}</template>
        </el-check-tag>
    </span>
</template>

<script setup lang="ts">
    import {watch} from "vue";

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    interface Label {
        key?: string;
        value: string;
    }

    const props = withDefaults(
        defineProps<{
            labels?: Label[];
            readOnly?: boolean;
            filterType?: "labels" | "metadata" | "type";
        }>(),
        {
            labels: () => [],
            readOnly: false,
            filterType: "labels",
        },
    );

    import {decodeSearchParams} from "../../components/filter/utils/helpers";
    let query: any[] = [];
    watch(
        () => route.query,
        (q) => (query = decodeSearchParams(q)),
        {immediate: true},
    );

    const isChecked = (label: Label) => {
        return query.some((l) => {
            if (props.filterType === "type") {
                return l.field === props.filterType && l.operation === "EQUALS" && typeof l.value === "string" && l.value === label.value;
            }

            if (typeof l?.value !== "string") return false;

            const [key, value] = l.value.split(":");
            return l.field === props.filterType && l.operation === "EQUALS" && key === label.key && value === label.value;
        });
    };

    const updateLabel = (label: Label) => {
        const getKey = (key?: string) => (props.filterType === "type"
            ? `filters[${props.filterType}][EQUALS]`
            : `filters[${props.filterType}][EQUALS][${key}]`);

        if (isChecked(label)) {
            const replacementQuery = {...route.query} as Record<string, any>;
            delete replacementQuery[props.filterType === "type" ? getKey() : getKey(label.key)];
            replacementQuery.page = "1";
            router.replace({query: replacementQuery});
        } else {
            const newQuery = {...route.query, page: "1"} as Record<string, any>;
            if (props.filterType === "type") {
                newQuery[getKey()] = label.value;
            } else {
                newQuery[getKey(label.key)] = label.value;
            }
            router.replace({query: newQuery});
        }
    };
</script>

<style scoped lang="scss">
.label {
    --ks-tag-background: #ECEBEF;
    --ks-tag-content: var(--ks-content-primary);
    --ks-tag-background-active: #414557;
    --ks-tag-content-active: var(--bs-white);

    html.dark & {
        --ks-tag-background: #5A6079;
        --ks-tag-background-active: #F2F2F2;
        --ks-tag-content-active: var(--bs-black);
    }

    background-color: var(--ks-tag-background);
    font-weight: normal;
    color: var(--ks-tag-content);
}

.label.el-check-tag.is-checked {
    background-color: var(--ks-tag-background-active);
    color: var(--ks-tag-content-active);
}
</style>
