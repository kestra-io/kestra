<template>
    <div class="filters-container">
        <!-- Left side - Customize filters and search -->
        <div class="filters-left">
            <el-button 
                type="text" 
                class="customize-btn"
                :icon="FilterIcon"
            >
                {{ $t("Customize filters") }}
            </el-button>
                
            <el-input
                v-model="searchValue"
                :placeholder="$t('Search executions')"
                :prefixIcon="MagnifyIcon"
                class="search-input"
                clearable
            />

            <!-- Center - Filter dropdowns -->
            <div class="filter-group">
                <span class="filter-label">{{ $t("Time Range") }}</span>
                <div class="filter-input">
                    <span class="filter-value">
                        {{ $t('in any value') }}
                    </span>
                    <PencilIcon 
                        class="edit-icon"
                        @click="openTimeRangeDialog"
                    />
                    <CloseCircleOutlineIcon 
                        class="clear-icon"
                        @click="timeRange = ''"
                    />
                </div>
            </div>

            <div class="filter-group">
                <span class="filter-label">{{ $t("Namespace") }}</span>
                <div class="filter-input">
                    <span class="filter-value">
                        {{ namespace || $t('in any value') }}
                    </span>
                    <PencilIcon 
                        class="edit-icon"
                        @click="openNamespaceDialog"
                    />
                    <CloseCircleOutlineIcon 
                        class="clear-icon"
                        @click="namespace = ''"
                    />
                </div>
            </div>

            <div class="filter-group">
                <span class="filter-label">{{ $t("State") }}</span>
                <div class="filter-input">
                    <span class="filter-value">
                        {{ state || $t('in any value') }}
                    </span>
                    <PencilIcon 
                        class="edit-icon"
                        @click="openStateDialog"
                    />
                    <CloseCircleOutlineIcon 
                        class="clear-icon"
                        @click="state = ''"
                    />
                </div>
            </div>
        </div>

        <!-- Right side - Action buttons -->
        <div class="filters-right">
            <el-button type="text" class="action-btn" :icon="ContentSaveIcon">
                {{ $t("Save filter") }}
            </el-button>
            <el-button type="text" class="action-btn" :icon="BookmarkIcon">
                {{ $t("Saved filter") }}
            </el-button>
            <el-button type="text" class="action-btn" :icon="TableIcon">
                {{ $t("Table options") }}
            </el-button>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import CloseCircleOutlineIcon from "vue-material-design-icons/Close.vue"
    import PencilIcon from "vue-material-design-icons/Pencil.vue"
    import FilterIcon from "vue-material-design-icons/Filter.vue"
    import ContentSaveIcon from "vue-material-design-icons/ContentSave.vue"
    import BookmarkIcon from "vue-material-design-icons/Bookmark.vue"
    import TableIcon from "vue-material-design-icons/Table.vue"
    import MagnifyIcon from "vue-material-design-icons/Magnify.vue"

    interface Props {
        namespaces?: string[]
    }

    withDefaults(defineProps<Props>(), {
        namespaces: () => []
    })

    const emit = defineEmits<{
        "update:search": [value: string]
        "update:time-range": [value: string]
        "update:namespace": [value: string]
        "update:state": [value: string]
        "save-filter": []
        "load-saved-filter": []
        "table-options": []
    }>()

    const searchValue = ref("")
    const timeRange = ref("")
    const namespace = ref("")
    const state = ref("")

    // Dialog functions for filter editing
    function openTimeRangeDialog() {
        // TODO: Implement time range selection dialog
    }

    function openNamespaceDialog() {
        // TODO: Implement namespace selection dialog
    }

    function openStateDialog() {
        // TODO: Implement state selection dialog
    }

    // Watch for changes and emit updates
    watch(searchValue, (newValue) => {
        emit("update:search", newValue)
    })

    watch(timeRange, (newValue) => {
        emit("update:time-range", newValue)
    })

    watch(namespace, (newValue) => {
        emit("update:namespace", newValue)
    })

    watch(state, (newValue) => {
        emit("update:state", newValue)
    })
</script>

<style lang="scss" scoped>
    .filters-container {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        background-color: var(--ks-background-card);
        border-radius: 8px;
        border: 1px solid var(--ks-border-primary);
        margin-bottom: 2rem;
        padding: 1rem;
        
        .filters-left {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-shrink: 0;
            
            .customize-btn {
                color: var(--ks-content-primary);
                font-size: 14px;
                padding: 8px 12px;
                border: 1px solid var(--ks-border-primary);
                
                :deep(.el-icon) {
                    color: var(--ks-content-tertiary);
                }
            }
            
            .search-container {
                
                .search-input {
                    :deep(.el-input__wrapper) {
                        background-color: var(--ks-background-input);
                        border: 1px solid var(--ks-border-primary);
                        border-radius: 8px;
                        
                        .el-input__inner {
                            color: var(--ks-content-primary);
                                                    font-size: 12px;

                            
                            &::placeholder {
                                color: var(--ks-content-tertiary);
                                                        font-size: 12px;

                            }
                        }
                        
                        .el-input__prefix {
                            .el-input__prefix-inner {
                                color: var(--ks-content-tertiary);
                                                        font-size: 12px;

                            }
                        }
                    }
                }
            }

                    .filters-center {
            display: flex;
            align-items: center;
            gap: 24px;
            justify-content: center;
            min-width: 0;
            flex: 1;
            max-width: 60%;
            
            .filter-group {
                display: inline-flex;
                align-items: center;
                flex-direction: row;
                flex-wrap: nowrap;
                border: 1px solid var(--ks-border-primary);
                border-radius: 8px;
                padding: 4px 12px;

                
                .filter-label {
                    color: var(--ks-content-primary);
                    font-size: 14px;
                    white-space: nowrap;
                }
                
                .filter-input {
                    display: flex;
                    align-items: center;
                    background-color: transparent;
                    border-radius: 6px;
                    padding-left: 4px;
                    gap: 8px;
                    cursor: pointer;
                    position: relative;
                    color: var(--ks-content-tertiary) !important;
                    
                    .filter-value {
                        flex: 1;
                        color: var(--ks-content-primary);
                        font-size: 13px;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    
                    .edit-icon {
                        color: var(--ks-content-tertiary);
                        cursor: pointer;
                        font-size: 14px;
                        flex-shrink: 0;
                    }
                    
                    .clear-icon {
                        color: var(--ks-content-tertiary);
                        cursor: pointer;
                        font-size: 16px;
                        flex-shrink: 0;
                    }
                }
            }
        }
        }
        

        
        .filters-right {
            display: flex;
            align-items: center;
            flex-shrink: 0;
            margin-left: auto;
            
            .action-btn {
                color: var(--ks-content-primary);
                font-size: 12px;
                display: flex;
                align-items: center;
                
                &:hover {
                    color: var(--ks-content-primary);
                }
                
                :deep(.el-icon) {
                    color: var(--ks-content-tertiary);
                    font-size: 16px;
                }
            }
        }
    }

</style>