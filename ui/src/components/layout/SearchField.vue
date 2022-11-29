<template>
    <el-input
        v-model="search"
        @input="onInput"
        :placeholder="$t('search')"
    >
        <template #suffix>
            <magnify />
        </template>
    </el-input>
</template>
<script>
    import {debounce} from "throttle-debounce";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    export default {
        emits: ["search"],
        components: {Magnify},
        created() {
            if (this.$route.query.q) {
                this.search = this.$route.query.q;
            }
            this.searchDebounce = debounce(300, () => {
                this.$emit("search", this.search);

                if (this.router) {
                    const query = {...this.$route.query, q: this.search, page: 1};
                    if (!this.search) {
                        delete query.q;
                    }
                    this.$router.push({query});
                }
            });
        },
        props: {
            router: {
                type: Boolean,
                default: true
            }
        },
        data() {
            return {
                search: ""
            };
        },
        methods: {
            onInput() {
                this.searchDebounce();
            },
        },
        unmounted() {
            this.searchDebounce.cancel();
        }
    };
</script>
