<template>
    <div class="properties-wrapper">
        <KestraIcon :tooltip="$t('properties.hint')" placement="bottom">
            <el-button :icon="TableColumn" @click.stop="toggleContainer">
                {{ $t("properties.label") }}
            </el-button>
        </KestraIcon>

        <div
            ref="containerRef"
            class="properties-container mt-2"
            :class="{visible: showContainer}"
            @click.stop
        >
            <el-input
                v-model="searchQuery"
                :placeholder="$t('search')"
                :prefix-icon="Magnify"
                class="rounded-2 w-100 mb-2"
            />
            <div class="pe-2 scrollable-container">
                <div class="mt-2 shown-group" v-if="shownProperties.length > 0">
                    <div class="text-start mb-1 group-title">
                        {{ $t("properties.shown") }}
                    </div>
                    <ul
                        class="property-list list-style-none m-0 p-0"
                        id="shown-list"
                    >
                        <li
                            v-for="property in filteredShownProperties"
                            :key="property"
                        >
                            <span class="property-name">{{
                                getColumnLabel(property)
                            }}</span>
                            <div
                                class="eye-icon"
                                @click="toggleProperty(property, true)"
                            >
                                <Eye />
                            </div>
                        </li>
                    </ul>
                </div>
                <el-divider v-if="filteredHiddenProperties.length > 0" />
                <div class="hidden-group">
                    <div
                        class="text-start mb-1 group-title"
                        id="hidden-title"
                        v-if="hiddenProperties.length > 0"
                    >
                        {{ $t("properties.hidden") }}
                    </div>
                    <ul
                        class="property-list list-style-none m-0 p-0"
                        id="hidden-list"
                    >
                        <li
                            v-for="property in filteredHiddenProperties"
                            :key="property"
                        >
                            <span class="property-name fw-bold">{{
                                getColumnLabel(property)
                            }}</span>
                            <div
                                class="eye-icon hidden"
                                @click="toggleProperty(property, false)"
                            >
                                <EyeOff />
                            </div>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
    import {ref, computed, onMounted, onUnmounted} from "vue";
    import KestraIcon from "../../Kicon.vue";
    import {Magnify, Eye, EyeOff, TableColumn} from "../utils/icons";

    const props = defineProps({
        columns: {type: Array, required: true},
        modelValue: {type: Array, required: true},
        storageKey: {type: String, required: true},
    });

    const emit = defineEmits(["updateProperties"]);
    const containerRef = ref(null);
    const showContainer = ref(false);
    const searchQuery = ref("");

    const handleClickOutside = (event) => {
        if (containerRef.value && !containerRef.value.contains(event.target)) {
            showContainer.value = false;
        }
    };

    onMounted(() => document.addEventListener("click", handleClickOutside));
    onUnmounted(() => document.removeEventListener("click", handleClickOutside));

    const shownProperties = computed(() => props.modelValue);
    const hiddenProperties = computed(() =>
        props.columns
            .map((col) => col.prop)
            .filter((prop) => !shownProperties.value.includes(prop)),
    );

    const toggleContainer = () => {
        showContainer.value = !showContainer.value;
    };

    const toggleProperty = (prop, isShown) => {
        const newValue = isShown
            ? shownProperties.value.filter((p) => p !== prop)
            : [...shownProperties.value, prop];

        localStorage.setItem(`columns_${props.storageKey}`, newValue.join(","));
        emit("updateProperties", newValue);
    };

    const getColumnLabel = (prop) => {
        const column = props.columns.find((col) => col.prop === prop);
        return column ? column.label : prop;
    };
    // Column list based on order defined in Table
    const filteredShownProperties = computed(() => {
        const query = searchQuery.value.toLowerCase();
        return props.columns
            .filter(
                (col) =>
                    shownProperties.value.includes(col.prop) &&
                    getColumnLabel(col.prop).toLowerCase().includes(query),
            )
            .map((col) => col.prop);
    });

    const filteredHiddenProperties = computed(() => {
        const query = searchQuery.value.toLowerCase();
        return props.columns
            .filter(
                (col) =>
                    hiddenProperties.value.includes(col.prop) &&
                    getColumnLabel(col.prop).toLowerCase().includes(query),
            )
            .map((col) => col.prop);
    });
</script>

<style scoped lang="scss">
.properties-wrapper {
    position: relative;
    display: inline-block;
}

.properties-container {
    position: absolute;
    z-index: 1000;
    background-color: var(--ks-background-body);
    border: 1px solid var(--ks-border-primary);
    border-radius: 8px;
    padding: 1rem;
    width: 280px;
    opacity: 0;
    visibility: hidden;
    top: 100%;
    right: 0;
    margin-bottom: 7px;
    transform: translateY(-10px);
    transition:
        opacity 0.3s ease,
        transform 0.3s ease,
        visibility 0.3s ease;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);

    &.visible {
        opacity: 1;
        visibility: visible;
        transform: translateY(0);
    }
}

.scrollable-container {
    max-height: 300px;
    overflow-y: scroll;
}

.container-header {
    position: relative;
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
}

.group-title {
    color: var(--ks-content-tertiary);
    font-size: var(--el-font-size-extra-small);
}

.property-list {
    li {
        padding: 8px 0;
        display: flex;
        justify-content: space-between;
        align-items: center;
        line-height: 10px;
    }
}

.property-name {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-regular);
}

:deep(.el-input__prefix-inner) {
    color: var(--bs-gray-700);
    font-size: var(--el-font-size-large);
}

.eye-icon {
    cursor: pointer;
    transition: opacity 0.2s;
    color: var(--el-text-color-regular);
    font-size: 16px;

    &:hover {
        color: var(--el-text-color-secondary);
    }

    &.hidden {
        color: var(--el-text-color-secondary);

        &:hover {
            color: var(--el-text-color-regular);
        }
    }
}

:deep(.el-input__inner::placeholder) {
    color: var(--bs-gray-700);
    font-size: var(--el-font-size-small);
}
</style>
