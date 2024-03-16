<template>
    <el-tooltip
        :content="$t('search filters.manage desc')"
        placement="bottom"
        :persistent="false"
        :hide-after="0"
        transition=""
    >
        <el-button :icon="ContentSave" @click="isDrawerOpen = !isDrawerOpen" />
    </el-tooltip>

    <drawer
        v-model="isDrawerOpen"
        :title="$t('search filters.manage')"
    >
        <el-card
            v-if="hasSavedFilters()"
            :header="$t('search filters.saved')"
            class="w-100"
        >
            <saved-filter
                v-for="(query, label) in relevantFilters"
                :key="label"
                :query="query"
                :label="label"
                @clicked="isDrawerOpen = false"
                @deleted="removeSavedFilter($event)"
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
            @keyup.enter="saveCurrentFilter()"
        >
            <template #append>
                <el-button
                    :icon="Plus"
                    :disabled="newFilterLabel === EMPTY_LABEL"
                    @click="saveCurrentFilter()"
                >
                    {{ $t('search filters.save filter') }}
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

    export default {
        components: {
            Drawer,
            SavedFilter
        },
        data() {
            return {
                isDrawerOpen: false,
                newFilterLabel: undefined,
                labelFilter: undefined
            };
        },
        computed: {
            ...mapGetters("filters", ["savedFilters"]),
            relevantFilters() {
                return Object.entries(this.savedFilters)
                    .filter(([key, _]) => this.labelFilter ? key.includes(this.labelFilter) : true)
                    .sort(([a, _], [b, __]) => {
                        const keyA = a.toLowerCase();
                        const keyB = b.toLowerCase();

                        return (keyA < keyB ? -1 : (keyA > keyB ? 1 : 0));
                    })
                    .reduce((acc, [key, value]) => { acc[key] = value; return acc; }, {});
            }
        },
        created() {
            this.resetNewFilterLabel();
            this.EMPTY_LABEL = "";
        },
        methods: {
            hasSavedFilters() {
                return Object.keys(this.savedFilters).length > 0;
            },
            saveCurrentFilter() {
                if (!this.newFilterLabel) {
                    return;
                }
                this.savedFilters[this.newFilterLabel] = this.$route.query;
                this.storeSavedFilters();
                this.resetNewFilterLabel();
            },
            resetNewFilterLabel() {
                this.newFilterLabel = this.EMPTY_LABEL;
            },
            removeSavedFilter(label) {
                delete this.savedFilters[label];
                this.storeSavedFilters();
            },
            storeSavedFilters() {
                this.$store.commit("filters/setSavedFilters", this.savedFilters);
            }
        }
    }
</script>