<template>
    <!-- No filter yet -->
    <el-button v-if="!hasSavedFilters" :icon="ContentSave" @click="toggleDrawer()">
        {{ $t("search filters.filters") }}
    </el-button>
    <!-- Existing filters -->
    <el-dropdown v-else button type="default" popper-class="disabled-means-selected">
        <el-button class="dropdown-button" :icon="ContentSave" @click="toggleDrawer()">
            {{ $t("search filters.filters") }}
        </el-button>
        <template #dropdown>
            <el-dropdown-menu>
                <template
                    v-for="(query, label) in relevantFilters"
                    :key="label"
                >
                    <el-dropdown-item @click="setFilter(query)" :disabled="isSelected(query)">
                        {{ label }}
                    </el-dropdown-item>
                </template>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
    <drawer
        v-model="isDrawerOpen"
        :title="$t('search filters.manage')"
    >
        <el-card
            v-if="hasSavedFilters"
            :header="$t('search filters.saved')"
            class="w-100"
        >
            <saved-filter
                v-for="(query, label) in relevantFilters"
                :key="label"
                :query="query"
                :label="label"
                @clicked="() => isDrawerOpen = false"
                @deleted="removeSavedFilter"
            />

            <template #footer>
                <el-input
                    v-model="labelFilter"
                    :prefix-icon="Magnify"
                    :maxlength="15"
                    :placeholder="$t('search')"
                    clearable
                />
            </template>
        </el-card>


        <el-input
            class="py-3"
            v-model="newFilterLabel"
            :autofocus="true"
            :maxlength="15"
            :placeholder="$t('search filters.filter name')"
            @keyup.enter="storeSavedFilters()"
        >
            <template #append>
                <el-button
                    :icon="Plus"
                    :disabled="newFilterLabel === EMPTY_LABEL"
                    @click="storeSavedFilters()"
                >
                    {{ $t("search filters.save filter") }}
                </el-button>
            </template>
        </el-input>
    </drawer>
</template>

<script setup>
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
</script>

<script>
    import Drawer from "../Drawer.vue";
    import SavedFilter from "./SavedFilter.vue";
    import {mapGetters} from "vuex";
    import _isEqual from "lodash/isEqual";

    export default {
        components: {
            Drawer,
            SavedFilter
        },
        props: {
            storageKey: {
                type: String,
                required: true
            }
        },
        data() {
            return {
                isDrawerOpen: false,
                newFilterLabel: undefined,
                labelFilter: undefined,
                filters: {}
            };
        },
        computed: {
            ...mapGetters("filters", ["savedFilters"]),
            relevantFilters() {
                return Object.entries(this.filters)
                    .filter(([key, _]) => this.labelFilter ? key.includes(this.labelFilter) : true)
                    .sort(([a, _], [b, __]) => {
                        const keyA = a.toLowerCase();
                        const keyB = b.toLowerCase();

                        return (keyA < keyB ? -1 : (keyA > keyB ? 1 : 0));
                    })
                    .reduce((acc, [key, value]) => {
                        acc[key] = value;
                        return acc;
                    }, {});
            },
            hasSavedFilters() {
                return Object.keys(this.filters).length > 0;
            }
        },
        created() {
            this.resetNewFilterLabel();
            this.EMPTY_LABEL = "";
        },
        mounted() {
            this.filters = this.savedFilters(this.storageKey);
        },
        methods: {
            resetNewFilterLabel() {
                this.newFilterLabel = this.EMPTY_LABEL;
            },
            removeSavedFilter(label) {
                delete this.filters[label];
                this.$store.commit("filters/setSavedFilters",
                                   {
                                       storageKey: this.storageKey,
                                       filters: this.filters
                                   });
            },
            storeSavedFilters() {
                if (!this.newFilterLabel) {
                    return;
                }
                this.filters[this.newFilterLabel] = this.$route.query;
                this.$store.commit("filters/setSavedFilters",
                                   {
                                       storageKey: this.storageKey,
                                       filters: this.filters
                                   });
                this.resetNewFilterLabel();
            },
            toggleDrawer() {
                this.isDrawerOpen = !this.isDrawerOpen;
            },
            setFilter(query) {
                this.$router.push({query: query})
            },
            isSelected(query) {
                return _isEqual(query, this.$route.query);
            }
        }
    }
</script>

<style lang="scss">
    .dropdown-button {
        width: 100%;
    }

    .disabled-means-selected {
        li.is-disabled {
            color: var(--bs-primary);
        }
    }
</style>