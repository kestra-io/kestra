<template>
    <nav data-component="FILENAME_PLACEHOLDER" class="d-flex w-100 gap-3 top-bar">
        <div class="d-flex flex-column flex-grow-1 flex-shrink-1 overflow-hidden top-title">
            <el-breadcrumb v-if="breadcrumb">
                <el-breadcrumb-item v-for="(item, x) in breadcrumb" :key="x" :class="{'pe-none': item.disabled}">
                    <router-link :to="!item.disabled ? item.link : {}">
                        {{ item.label }}
                    </router-link>
                </el-breadcrumb-item>
            </el-breadcrumb>
            <h1 class="h5 fw-semibold m-0 d-inline-flex">
                <slot name="title">
                    {{ title }}
                    <el-tooltip v-if="description" :content="description">
                        <Information class="ms-2" />
                    </el-tooltip>
                    <Badge v-if="beta" label="Beta" />
                </slot>
                <el-button
                    class="star-button"
                    :class="{'star-active': bookmarked}"
                    :icon="StarOutlineIcon"
                    circle
                    @click="onStarClick"
                />
            </h1>
        </div>
        <div class="d-lg-flex side gap-2 flex-shrink-0 align-items-center mycontainer">
            <div class="d-none d-lg-flex align-items-center">
                <global-search class="trigger-flow-guided-step" />
            </div>
            <div class="d-flex side gap-2 flex-shrink-0 align-items-center">
                <el-button v-if="shouldDisplayDeleteButton && logsStore.logs !== undefined && logsStore.logs.length > 0" @click="deleteLogs()">
                    <TrashCan class="me-2" />
                    <span>{{ $t("delete logs") }}</span>
                </el-button>
            </div>
            <slot name="additional-right" />
            <div class="d-flex fixed-buttons icons">
                <impersonating />
            </div>
        </div>
    </nav>
</template>

<script>
    import {mapState, mapGetters} from "vuex";
    import {mapStores} from "pinia";
    import {useLogsStore} from "../../stores/logs";
    import Impersonating from "override/components/auth/Impersonating.vue";
    import GlobalSearch from "./GlobalSearch.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import StarOutlineIcon from "vue-material-design-icons/StarOutline.vue";
    import StarIcon from "vue-material-design-icons/Star.vue";
    import Information from "vue-material-design-icons/Information.vue"
    import Badge from "../global/Badge.vue";

    export default {
        components: {
            GlobalSearch,
            TrashCan,
            Impersonating,
            Information,
            Badge
        },
        props: {
            title: {
                type: String,
                required: true
            },
            description: {
                type: String,
                default: undefined
            },
            breadcrumb: {
                type: Array,
                default: undefined
            },
            beta: {
                type: Boolean,
                required: false
            },
        },
        created() {
            this.logsStore.setVuexStore(this.$store);
        },
        computed: {
            ...mapState("core", ["tutorialFlows"]),
            ...mapState("bookmarks", ["pages"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("auth", ["user"]),
            ...mapStores(useLogsStore),
            tourEnabled(){
                // Temporary solution to not showing the tour menu item for EE
                return this.tutorialFlows?.length && !Object.keys(this.user).length
            },
            shouldDisplayDeleteButton() {
                return this.$route.name === "flows/update" && this.$route.params?.tab === "logs"
            },
            StarOutlineIcon() {
                return this.bookmarked ? StarIcon : StarOutlineIcon
            },
            bookmarked() {
                return this.pages.some(page => page.path === this.currentFavURI)
            },
            currentFavURI() {
                // make sure the value changes when the route changes
                // by mentionning the route in the computed properties
                // we create a hook into vues reactivity system to update when it updates
                if(this.$route) {
                    return window.location.pathname
                        + window.location.search
                            // remove the parameters that are permanently changing
                            .replace(/&?page=[^&]*/ig, "")
                            // fix if this resulted in a "?&" url
                            .replace(/\?&/, "?")
                }
                return ""
            }
        },
        methods: {
            restartGuidedTour() {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {tourStarted: true});

                this.$tours["guidedTour"]?.start();
            },
            deleteLogs() {
                this.$toast().confirm(
                    this.$t("delete_all_logs"),
                    () => this.logsStore.deleteLogs({namespace: this.namespace, flowId: this.flowId}),
                    () => {}
                )
            },
            onStarClick() {
                if (this.bookmarked) {
                    this.$store.dispatch("bookmarks/remove", {
                        path: this.currentFavURI
                    })
                } else {
                    this.$store.dispatch("bookmarks/add", {
                        path: this.currentFavURI,
                        label: this.breadcrumb?.length ? `${this.breadcrumb[this.breadcrumb.length-1].label}: ${this.title}` : this.title,
                    })
                }
            }
        },
    };
</script>

<style lang="scss" scoped>
    nav {
        top: 0;
        position: sticky;
        z-index: 1000;
        padding: 1rem 2rem;
        border-bottom: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);

        .top-title, h1, .el-breadcrumb {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        h1 {
            line-height: 1.6;
            display: flex !important;
            align-items: center;
        }

        .star-button{
            margin-left: 1rem;
            border: none;
        }

        .star-active {
            color: #9470FF;
        }

        :deep(.el-breadcrumb__item) {
            display: inline-block;
        }


        :deep(.el-breadcrumb__inner) {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        .side {
            .fixed-buttons {
                align-items: center;

                button, :deep(button), a, :deep(a) {
                    border: none;
                    font-size: var(--font-size-lg);
                    padding: .25rem;
                }
            }

            :slotted(ul), :deep(ul) {
                display: flex;
                list-style: none;
                padding: 0;
                margin: 0;
                gap: .5rem;
                align-items: center;
            }
        }
        @media (max-width: 768px) {
            .mycontainer{
                display:grid;
                grid-template-columns:repeat(3, minmax(0,auto));
                grid-template-rows: repeat(2, auto);
                gap:10px;
                overflow: hidden;


            }
            .icons{
                grid-row:2;
                grid-column:2;
                display: contents;
            }

        }
        @media (max-width: 664px){
            .mycontainer{
                display:grid;
                grid-template-columns:repeat(2, minmax(0,auto));
                grid-template-rows: repeat(2, auto);
                gap:10px;
                overflow: hidden;

            }
        }
    }
</style>
