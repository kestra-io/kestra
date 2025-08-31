<template>
    <span v-if="props.labels.length">
        <el-check-tag
            v-for="(label, index) in props.labels"
            :key="index"
            :disabled="readOnly"
            :checked="isChecked(label)"
            @change="updateLabel(label)"
            class="me-1 el-tag label"
        >
            {{ label.key }}:{{ label.value }}
        </el-check-tag>
    </span>
</template>

<script setup lang="ts">
    import {watch} from "vue";

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    interface Label {
        key: string;
        value: string;
    }

    const props = withDefaults(
        defineProps<{ labels: Label[]; readOnly?: boolean }>(),
        {labels: () => [], readOnly: false},
    );

    import {decodeSearchParams} from "../../components/filter/utils/helpers";
    let query: any[] = [];
    watch(
        () => route.query,
        (q: any) => (query = decodeSearchParams(q, undefined, [])),
        {immediate: true},
    );

    const isChecked = (label: Label) => {
        return query.some((l) => {
            if (typeof l?.value !== "string") return false;

            const [key, value] = l.value.split(":");
            return key === label.key && value === label.value;
        });
    };

    const updateLabel = (label: Label) => {
        const getKey = (key: string) => `filters[labels][EQUALS][${key}]`;

        if (isChecked(label)) {
            const replacementQuery = {...route.query};
            delete replacementQuery[getKey(label.key)];
            router.replace({query: replacementQuery});
        } else {
            router.replace({
                query: {...route.query, [getKey(label.key)]: label.value},
            });
        }
    };
</script>

<style scoped lang="scss">
.label {
    #{--ks-tag-background}: #E0E3F0;
    #{--ks-tag-background-active}: #B8BDD4;

    html.dark & {
        #{--ks-tag-background}: #404559;
        #{--ks-tag-background-active}: #59607B;
    }
    
    background-color: var(--ks-tag-background);
    font-weight: normal;
    color: var(--ks-content-primary);
}

.el-check-tag.el-check-tag--primary.is-checked {
    background-color: var(--ks-tag-background-active);
    color: var(--ks-content-primary);
}
</style>
