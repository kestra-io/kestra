<template>
    <b-nav-form>
        <b-form-input
            :label="$t('search').capitalize()"
            size="sm"
            class="mr-sm-2"
            placeholder="Search"
        ></b-form-input>
    </b-nav-form>
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
                                `${f.key}:*${this.search}* OR ${f.key}:${this.search}`
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