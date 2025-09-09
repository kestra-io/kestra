<template>
    <span data-component="FILENAME_PLACEHOLDER" v-if="description">
        <a @click="open()">
            <HelpCircle
                title=""
                :id="'tooltip-desc-' + id"
            />
        </a>

        <Drawer
            v-if="isOpen"
            v-model="isOpen"
            :title="title"
        >
            <Markdown class="markdown-tooltip" :source="description" />
        </Drawer>
    </span>
</template>

<script>
    import HelpCircle from "vue-material-design-icons/HelpCircle.vue";
    import Markdown from "./Markdown.vue";
    import Drawer from "../Drawer.vue";

    export default {
        components: {
            HelpCircle,
            Markdown,
            Drawer
        },
        data() {
            return {
                isOpen: false,
            };
        },
        props: {
            id: {
                type: String,
                required: true
            },
            title: {
                type: String,
                default: "",
            },
            description: {
                type: String,
                default: "",
            }
        },
        methods: {
            open() {
                this.isOpen = true
            }
        }
    };
</script>

<style lang="scss" scoped>
    a {
        cursor: pointer;
    }
</style>