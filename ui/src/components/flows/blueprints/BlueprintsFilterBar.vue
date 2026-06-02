<template>
    <div :class="{'embed-top': showEmbed}">
        <KsRow :class="{'mb-3': !showEmbed}" justify="center">
            <KsFilter
                :configuration="blueprintFilter"
                :buttons="FILTER_BUTTONS"
                searchInputFullWidth
                @search="emit('search', $event)"
            />
        </KsRow>

        <div v-if="showEmbed && tagList.length" class="embed-tag-pills">
            <KsCheckTag
                pill
                :checked="modelValue.length === 0"
                @change="emit('update:modelValue', [])"
            >
                {{ $t("blueprints.all") }}
            </KsCheckTag>

            <KsCheckTag
                v-for="tag in tagList"
                :key="tag.id"
                pill
                :checked="modelValue.includes(tag.id)"
                @change="toggleTag(tag.id)"
            >
                {{ tag.name }}
            </KsCheckTag>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsFilter} from "@kestra-io/design-system"
    import {useBlueprintFilter} from "../../filter/configurations"
    import type {BlueprintTag} from "../../../stores/blueprints"

    const props = defineProps<{
        embed: boolean;
        system: boolean;
        tags?: Record<string, BlueprintTag>;
        modelValue: string[];
    }>()

    const emit = defineEmits<{
        search: [query: string];
        "update:modelValue": [value: string[]];
    }>()

    const FILTER_BUTTONS = {
        savedFilters: {shown: false},
        tableOptions: {shown: false},
    } as const

    const blueprintFilter = useBlueprintFilter()

    const showEmbed = computed(() => props.embed && !props.system)
    const tagList = computed(() => Object.values(props.tags ?? {}))

    const toggleTag = (id: string) => {
        const isOnlySelected = props.modelValue.length === 1 && props.modelValue[0] === id
        emit("update:modelValue", isOnlySelected ? [] : [id])
    }
</script>

<style lang="scss" scoped>
    .embed-top {
        display: flex;
        flex-direction: column;
        margin-inline: calc(var(--ks-data-table-gutter) * -1);
        padding: 0 1.25rem var(--ks-spacing-3);
        border-bottom: 1px solid var(--ks-border-default);
    }

    .embed-tag-pills {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }
</style>
