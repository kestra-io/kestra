<template>
    <b-nav-form @submit.prevent>
        <b-form-input
            :label="$t('search')"
            size="sm"
            @input="onSearch"
            v-model="search"
            :placeholder="$t('search')"
        ></b-form-input>
    </b-nav-form>
</template>
<script>
import { debounce } from "throttle-debounce";
export default {
    created() {
        if (this.$route.query.q) {
            this.search = this.$route.query.q;
        }
        this.searchDebounce = debounce(300, () => {
            this.$emit("onSearch", this.search);
        });
    },
    data() {
        return {
            search: ""
        };
    },
    methods: {
        onSearch() {
            const query = { ...this.$route.query, q: this.search, page: 1 };
            if (!this.search) {
                delete query.q;
            }
            this.$router.push({ query });
            this.searchDebounce();
        },
    },
    destroyed() {
        this.searchDebounce.cancel();
    }
};
</script>
