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
            <el-col :span="12">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionLeft">
                        <el-option
                            v-for="item in options"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionLeft, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('see full revision') }}</span>
                        </el-button>
                        <el-button :icon="Restore" :disabled="revisionNumber(revisionLeft) === flow.revision" @click="restoreRevision(revisionLeft, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <el-alert v-if="revisionLeftError" type="warning" show-icon :closable="false" class="mb-0 mt-3">
                    <strong>{{ $t('invalid source') }}</strong><br>
                    {{ revisionLeftError }}
                </el-alert>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionLeft)}" />
            </el-col>
            <el-col :span="12">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionRight">
                        <el-option
                            v-for="item in options"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionRight, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('see full revision') }}</span>
                        </el-button>
                        <el-button :icon="Restore" :disabled="revisionNumber(revisionRight) === flow.revision" @click="restoreRevision(revisionRight, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ $t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <el-alert v-if="revisionRightError" type="warning" show-icon :closable="false" class="mb-0 mt-3">
                    <strong>{{ $t('invalid source') }}</strong><br>
                    {{ revisionRightError }}
                </el-alert>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: $route.params.namespace, flowId: $route.params.id, revision: revisionNumber(revisionRight)}" />
            </el-col>
        </el-row>

        <editor
            class="mt-1"
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

            <editor v-model="revisionYaml" lang="yaml" />
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
    import YamlUtils from "../../utils/yamlUtils";
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
                this.$store
                    .dispatch("flow/loadRevisions", this.$route.params)
                    .then(() => {
                        const revisionLength = this.revisions.length;
                        if (revisionLength > 0) {
                            this.revisionRight = revisionLength - 1;
                        }
                        if (revisionLength > 1) {
                            this.revisionLeft = revisionLength - 2;
                        }
                        if (this.$route.query.revisionRight) {
                            this.revisionRight = this.revisionIndex(
                                this.$route.query.revisionRight
                            );
                            if (
                                !this.$route.query.revisionLeft &&
                                this.revisionRight > 0
                            ) {
                                this.revisionLeft = this.revisions.length - 1;
                            }
                        }
                        if (this.$route.query.revisionLeft) {
                            this.revisionLeft = this.revisionIndex(
                                this.$route.query.revisionLeft
                            );
                        }
                    });
            },
            revisionIndex(revision) {
                const rev = parseInt(revision);
                for (let i = 0; i < this.revisions.length; i++) {
                    if (rev === this.revisions[i].revision) {
                        return i;
                    }
                }
            },
            revisionNumber(index) {
                return this.revisions[index].revision;
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
                    ...{revisionLeft:this.revisionLeft + 1, revisionRight: this.revisionRight + 1}}
                });
            },
            transformRevision(source) {
                if (source.exception) {
                    return YamlUtils.stringify(YamlUtils.parse(source.source));
                }

                return source.source ? source.source : YamlUtils.stringify(source);
            }
        },
        computed: {
            ...mapState("flow", ["flow", "revisions"]),
            options() {
                return (this.revisions || []).map((revision, x) => {
                    return {
                        value: x,
                        text: revision.revision,
                    };
                });
            },
            revisionLeftError() {
                if (this.revisionLeft === undefined) {
                    return "";
                }

                return this.revisions[this.revisionLeft].exception
            },
            revisionRightError() {
                if (this.revisionRight === undefined) {
                    return "";
                }

                return this.revisions[this.revisionRight].exception
            },
            revisionLeftText() {
                if (this.revisionLeft === undefined) {
                    return "";
                }

                return this.transformRevision(this.revisions[this.revisionLeft]);
            },
            revisionRightText() {
                if (this.revisionRight === undefined) {
                    return "";
                }

                return this.transformRevision(this.revisions[this.revisionRight]);
            },
        },
        data() {
            return {
                revisionLeft: 0,
                revisionRight: 0,
                revision: undefined,
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
        padding-bottom: var(--spacer);
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