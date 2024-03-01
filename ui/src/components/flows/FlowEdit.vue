<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb">
        <template #additional-right v-if="canSave || canDelete || canExecute">
            <ul>
                <li>
                    <el-button :icon="icon.Delete" size="large" v-if="canDelete" @click="deleteFile">
                        {{ $t('delete') }}
                    </el-button>
                </li>

                <li>
                    <router-link v-if="flow && canCreate" :to="{name: 'flows/create', query: {copy: true}}">
                        <el-button :icon="icon.ContentCopy" size="large">
                            {{ $t('copy') }}
                        </el-button>
                    </router-link>
                </li>

                <li>
                    <trigger-flow v-if="flow && canExecute" :disabled="flow.disabled" :flow-id="flow.id" type="default" :namespace="flow.namespace" />
                </li>

                <li>
                    <el-button class="edit-flow-save-button" :icon="icon.ContentSave" size="large" @click="save" v-if="canSave" type="primary">
                        {{ $t('save') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <div class="mt-3 edit-flow-div">
        <editor @save="save" v-model="content" schema-type="flow" lang="yaml" @update:model-value="onChange($event)" @cursor="updatePluginDocumentation" />
        <div id="guided-right" />
    </div>
</template>

<script>
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import {mapGetters, mapState} from "vuex";
    import TriggerFlow from "./TriggerFlow.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import {shallowRef} from "vue";
    import TopNavBar from "../layout/TopNavBar.vue"

    export default {
        components: {
            TriggerFlow,
            TopNavBar
        },
        mixins: [flowTemplateEdit],
        data() {
            return {
                dataType: "flow",
                icon: {
                    ContentCopy: shallowRef(ContentCopy),
                    ContentSave: shallowRef(ContentSave),
                    Delete: shallowRef(Delete),
                },
                lastChangeWasGuided: false,
            };
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapState("flow", ["total"])
        },
        methods: {
            stopTour() {
                this.$tours["guidedTour"].stop();
                this.$store.commit("core/setGuidedProperties", {
                    ...this.guidedProperties,
                    tourStarted: false
                });
            },
        },
        created() {
            this.loadFile();
        },
        mounted() {
            setTimeout(() => {
                if (!this.guidedProperties.tourStarted
                    && localStorage.getItem("tourDoneOrSkip") !== "true"
                    && this.total === 0) {
                    this.$tours["guidedTour"].start();
                }
            }, 200)
            window.addEventListener("popstate", () => {
                this.stopTour();
            });
        },
        watch: {
            guidedProperties: function () {
                if (localStorage.getItem("tourDoneOrSkip") !== "true") {
                    if (this.guidedProperties.source !== undefined) {
                        this.content = this.guidedProperties.source
                        this.lastChangeWasGuided = true;
                    }
                    if (this.guidedProperties.saveFlow) {
                        this.save();
                    }
                }
                else if(this.lastChangeWasGuided) {
                    this.content = "";
                    this.lastChangeWasGuided = false;
                }
            }
        }
    };
</script>
