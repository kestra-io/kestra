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
            const query = { ...this.$route.query, q: this.search };
            if (!this.search) {
                delete query.q;
            }
            this.$router.push({ query });
            this.searchDebounce();
        }
    },
    destroyed() {
        this.searchDebounce.cancel();
    }
};
</script>