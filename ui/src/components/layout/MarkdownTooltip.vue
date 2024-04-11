<template>
    <span v-if="description">
        <a @click="open()">
            <help-circle
                title=""
                :id="'tooltip-desc-' + id"
            />
        </a>

        <drawer
            v-if="isOpen"
            v-model="isOpen"
            :title="title"
        >
            <markdown class="markdown-tooltip" :source="description" />
        </drawer>
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