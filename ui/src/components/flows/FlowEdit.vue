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
                    <router-link v-if="flowStore.flow && canCreate" :to="{name: 'flows/create', query: {copy: true}}">
                        <el-button :icon="icon.ContentCopy" size="large">
                            {{ $t('copy') }}
                        </el-button>
                    </router-link>
                </li>

                <li>
                    <trigger-flow v-if="flowStore.flow && canExecute" :disabled="flowStore.flow.disabled" :flow-id="flowStore.flow.id" type="default" :namespace="flowStore.flow.namespace" />
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
        <editor @save="save" v-model="content" schema-type="flow" lang="yaml" @update:model-value="onChange" @cursor="updatePluginDocumentation" />
    </div>
</template>

<script>
    import {shallowRef} from "vue";
    import {mapStores} from "pinia";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import {useCoreStore} from "../../stores/core";
    import flowTemplateEdit from "../../mixins/flowTemplateEdit";
    import TriggerFlow from "./TriggerFlow.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import {useFlowStore} from "../../stores/flow";

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
            ...mapStores(useCoreStore, useFlowStore),
        },
        methods: {
            stopTour() {
                this.$tours["guidedTour"]?.stop();
                this.coreStore.guidedProperties = {...this.coreStore.guidedProperties, tourStarted: false};
            },
        },
        created() {
            this.loadFile();
        },
        mounted() {
            setTimeout(() => {
                if (!this.guidedProperties.tourStarted
                    && localStorage.getItem("tourDoneOrSkip") !== "true"
                    && this.flowStore.total === 0) {
                    this.$tours["guidedTour"]?.start();
                }
            }, 200)
            window.addEventListener("popstate", () => {
                this.stopTour();
            });
        }
    };
</script>
