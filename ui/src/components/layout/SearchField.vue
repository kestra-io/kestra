<template>
    <b-form-group :label="$t('search').capitalize()" label-cols-sm="auto">
        <b-form-input @input="onSearch" v-model="search" :placeholder="$t('type anything')"></b-form-input>
    </b-form-group>
</template>
<script>
import { debounce } from "throttle-debounce";

export default {
    props: {
        fields: {
            type: Array,
            required: true
        }
    },
    created() {
        this.searchDebounce = debounce(300, () => {
            let q = "*";
            if (this.search) {
                q =
                    this.fields
                        .map(
                            f =>
                                `${f.key}:*${this.search}* OR ${f.key}:${
                                    this.search
                                }`
                        )
                        .join(" OR ") || "*";
            }
            this.$emit("onSearch", q);
        });
    },
    data() {
        return {
            search: ""
        };
    },
    methods: {
        onSearch() {
            this.searchDebounce();
        }
    },
    destroyed() {
        this.searchDebounce.cancel();
    }
};
</script>