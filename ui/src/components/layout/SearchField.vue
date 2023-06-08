<template>
    <el-input
        v-model="search"
        @input="onInput"
        :placeholder="$t(placeholder)"
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
        searchDebounce: undefined,
        created() {
            this.init();
        },
        props: {
            router: {
                type: Boolean,
                default: true
            },
            placeholder: {
                type: String,
                required: false,
                default: 'search'
            },
            embed: {
                type: Boolean,
                default: false
            }
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.init()
                }
            }
        },
        data() {
            return {
                search: ""
            };
        },
        methods: {
            init() {
                if (this.$route.query.q) {
                    this.search = this.$route.query.q;
                }
                this.searchDebounce = debounce(300, () => {
                    this.$emit("search", this.search);

                    if (this.router && !this.embed) {
                        const query = {...this.$route.query, q: this.search, page: 1};
                        if (!this.search) {
                            delete query.q;
                        }
                        this.$router.push({query});
                    }
                });
            },
            onInput() {
                this.searchDebounce();
            },
        },
        unmounted() {
            this.searchDebounce && this.searchDebounce.cancel();
        }
    };
</script>
