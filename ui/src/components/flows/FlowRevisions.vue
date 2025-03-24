<template>
    <div class="flow-revision" v-if="revisions && revisions.length > 1">
        <el-select v-model="sideBySide" class="mb-3">
            <el-option
                v-for="item in displayTypes"
                :key="item.value"
                :label="item.text"
                :value="item.value"
            />
        </el-select>
        <el-row :gutter="15">
            <el-col :span="12" v-if="revisionLeftIndex !== undefined">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionLeftIndex" @change="addQuery">
                        <el-option
                            v-for="item in options(revisionRightIndex)"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionLeftIndex, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('see full revision') }}</span>
                        </el-button>
                        <el-button :icon="Restore" :disabled="revisionNumber(revisionLeftIndex) === flow.revision" @click="restoreRevision(revisionLeftIndex, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionLeftIndex)}" />
            </el-col>
            <el-col :span="12" v-if="revisionRightIndex !== undefined">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionRightIndex" @change="addQuery">
                        <el-option
                            v-for="item in options(revisionLeftIndex)"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionRightIndex, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('see full revision') }}</span>
                        </el-button>
                        <el-button :icon="Restore" :disabled="revisionNumber(revisionRightIndex) === flow.revision" @click="restoreRevision(revisionRightIndex, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionRightIndex)}" />
            </el-col>
        </el-row>

        <editor
            class="mt-1"
            v-if="revisionLeftText && revisionRightText"
            :diff-side-by-side="sideBySide"
            :model-value="revisionRightText"
            :original="revisionLeftText"
            lang="yaml"
            :show-doc="false"
        />

        <drawer v-if="isModalOpen" v-model="isModalOpen">
            <template #header>
                <h5>{{ $t("revision") + `: ` + revision }}</h5>
            </template>

            <editor v-model="revisionYaml" lang="yaml" :full-height="false" :input="true" :navbar="false" :read-only="true" />
        </drawer>
    </div>
    <div v-else>
        <el-alert class="mb-0" show-icon :closable="false">
            {{ $t('no revisions found') }}
        </el-alert>
    </div>
</template>

<script setup>
    import FileCode from "vue-material-design-icons/FileCode.vue";
    import Restore from "vue-material-design-icons/Restore.vue";
</script>

<script>
    import {mapState} from "vuex";
    import Editor from "../../components/inputs/Editor.vue";
    import Crud from "override/components/auth/Crud.vue";
    import Drawer from "../Drawer.vue";
    import {saveFlowTemplate} from "../../utils/flowTemplate";

    export default {
        components: {Editor, Crud, Drawer},
        created() {
            this.load();
        },
        methods: {
            load() {
                const currentRevision = this.flow.revision;

                this.revisions = [...Array(currentRevision).keys()].map(((k, i) => {
                    if (currentRevision === this.revisionNumber(i)) {
                        return this.flow;
                    }
                    return {revision: i + 1};
                }));

                if (this.$route.query.revisionRight) {
                    this.revisionRightIndex = this.revisionIndex(
                        this.$route.query.revisionRight
                    );
                    if (
                        !this.$route.query.revisionLeft &&
                        this.revisionRightIndex > 0
                    ) {
                        this.revisionLeftIndex = this.revisionRightIndex - 1;
                    }
                } else if (currentRevision > 0) {
                    this.revisionRightIndex = currentRevision - 1;
                }

                if (this.$route.query.revisionLeft) {
                    this.revisionLeftIndex = this.revisionIndex(
                        this.$route.query.revisionLeft
                    );
                } else if (currentRevision > 1) {
                    this.revisionLeftIndex = currentRevision - 2;
                }
            },
            revisionIndex(revision) {
                const revisionInt = parseInt(revision);

                if (revisionInt < 1 || revisionInt > this.revisions.length) {
                    return undefined;
                }

                return revisionInt - 1;
            },
            revisionNumber(index) {
                return index + 1;
            },
            seeRevision(index, revision) {
                this.revisionId = index
                this.revisionYaml = revision
                this.revision = this.revisionNumber(index)
                this.isModalOpen = true;
            },
            restoreRevision(index, revision) {
                this.$toast()
                    .confirm(this.$t("restore confirm", {revision: this.revisionNumber(index)}), () => {
                        return saveFlowTemplate(this, revision, "flow")
                            .then(this.load)
                            .then(() => {
                                this.$router.push({query: {}});
                            });
                    });
            },
            addQuery() {
                this.$router.push({query: {
                    ...this.$route.query,
                    ...{revisionLeft:this.revisionLeftIndex + 1, revisionRight: this.revisionRightIndex + 1}}
                });
            },
            async fetchRevision(revision) {
                const revisionFetched = await this.$store.dispatch("flow/loadFlow", {
                    namespace: this.flow.namespace,
                    id: this.flow.id,
                    revision,
                    allowDeleted: true,
                    store: false
                });
                this.revisions[this.revisionIndex(revision)] = revisionFetched;

                return revisionFetched;
            },
            options(excludeRevisionIndex) {
                return this.revisions
                    .filter((_, index) => index !== excludeRevisionIndex)
                    .map(({revision}) => ({value: this.revisionIndex(revision), text: revision}));
            }
        },
        computed: {
            ...mapState("flow", ["flow"])
        },
        watch: {
            revisionLeftIndex: async function (newValue, oldValue) {
                if (newValue === oldValue) {
                    return;
                }

                if (newValue === undefined) {
                    this.revisionLeftText = undefined;
                }

                const leftRevision = this.revisions[newValue];
                let source = leftRevision.source;
                if (!source) {
                    source = (await this.fetchRevision(leftRevision.revision)).source;
                }

                this.revisionLeftText = source;
            },
            revisionRightIndex: async function (newValue, oldValue) {
                if (newValue === oldValue) {
                    return;
                }

                if (newValue === undefined) {
                    this.revisionRightText = undefined;
                }

                const rightRevision = this.revisions[newValue];
                let source = rightRevision.source;
                if (!source) {
                    source = (await this.fetchRevision(rightRevision.revision)).source;
                }

                this.revisionRightText = source;
            }
        },
        data() {
            return {
                revisionLeftIndex: undefined,
                revisionRightIndex: undefined,
                revisionLeftText: undefined,
                revisionRightText: undefined,
                revision: undefined,
                revisions: [],
                revisionId: undefined,
                revisionYaml: undefined,
                sideBySide: true,
                displayTypes: [
                    {value: true, text: this.$t("side-by-side")},
                    {value: false, text:  this.$t("line-by-line")},
                ],
                isModalOpen: false
            };
        },
    };
</script>

<style scoped lang="scss">
    .flow-revision {
        display: flex;
        flex-direction: column;
        min-height: 100%;
    }

    .ks-editor {
        flex: 1;
        padding-bottom: 1rem;
    }

    .revision-select {
        display: flex;

        > div {
            &:first-child {
                flex: 2;
            }
        }
    }
</style>