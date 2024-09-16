<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb">
        <template #additional-right v-if="canSave || canDelete">
            <ul>
                <li>
                    <el-button :icon="Delete" size="large" type="default" v-if="canDelete" @click="deleteFile">
                        {{ $t('delete') }}
                    </el-button>

                    <template v-if="canSave">
                        <el-button :icon="ContentSave" @click="save" type="primary" size="large">
                            {{ $t('save') }}
                        </el-button>
                    </template>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <templates-deprecated />
    <div>
        <editor class="main-editor" @save="save" v-model="content" schema-type="template" lang="yaml" @update:model-value="onChange($event)" @cursor="updatePluginDocumentation" />
    </div>
</template>

<script setup>
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import TemplatesDeprecated from "./TemplatesDeprecated.vue";
    import TopNavBar from "../layout/TopNavBar.vue"
</script>

<script>
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import {mapState} from "vuex";

    export default {
        mixins: [flowTemplateEdit],
        data() {
            return {
                dataType: "template",
            };
        },
        computed: {
            ...mapState("template", ["template"]),
        },
        watch: {
            "$route.params"() {
                this.reload()
            },
        },
        created() {
            this.reload()
        },
        unmounted() {
            this.$store.commit("template/setTemplate", undefined);
        },
        methods: {
            reload() {
                if (this.$route.name === "templates/update") {
                    this.$store
                        .dispatch("template/loadTemplate", this.$route.params)
                        .then(this.loadFile);
                }
            },
            onChange() {
                this.$store.dispatch("core/isUnsaved", this.previousContent !== this.content);
            }
        }
    };
</script>
<style lang="scss" scoped>
    .main-editor {
        height: 100vh;

    }

</style>
