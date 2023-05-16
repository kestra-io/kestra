<template>
    <el-card
        class="description"
        v-if="source"
        :header="$t('description')"
    >
        <div>
            <markdown class="markdown-tooltip" :source="source" />
        </div>
    </el-card>
</template>
<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapGetters} from "vuex";

    export default {
        components: {
            Markdown
        },
        props: {
            description: {
                type: String,
                default: undefined
            },
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
            source() {
                return this.description || this.flow?.description;
            }
        }
    };
</script>

<style lang="scss" scoped>
    .description {
        font-size: var(--font-size-sm);
        .card-body > div {
            max-height: 300px;
            padding-right: 0.25rem;
            overflow: auto;
        }
    }
</style>