<template>
    <KsDropdownItem v-if="configs?.version" divided command="version">
        <KsPopover width="max-content" trigger="hover" placement="right" :showArrow="false" :offset="24">
            <template #reference>
                <span class="version-item">
                    <KsIcon size="base">
                        <InformationOutline />
                    </KsIcon>
                    {{ $t("version") }}
                    <span class="version-item__badge">v{{ configs.version.split("-")[0] }}</span>
                </span>
            </template>

            <template #default>
                <div class="version-item__details">
                    <div>{{ $t("version") }}: {{ configs.version }}</div>
                    <div v-if="configs.commitId">
                        {{ $t("commit_id") }}:
                        <span class="version-item__commit">{{ configs.commitId }}</span>
                    </div>
                    <div v-if="configs.commitDate">
                        {{ $t("date") }}: {{ dateUtils.dateFilter(configs.commitDate) }}
                    </div>
                </div>
            </template>
        </KsPopover>
    </KsDropdownItem>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import {KsDropdownItem, KsIcon, KsPopover, dateUtils} from "@kestra-io/design-system"
    import {useMiscStore} from "override/stores/misc"

    const miscStore = useMiscStore()

    const configs = computed(() => miscStore.configs)
</script>

<style scoped lang="scss">
    .version-item {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
    }

    .version-item__badge {
        margin-left: auto;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-regular);
        line-height: 1;
        white-space: nowrap;
    }

    .version-item__details {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-regular);
    }

    .version-item__commit {
        color: var(--ks-text-link);
        font-family: var(--kel-font-family-monospace);
    }
</style>
