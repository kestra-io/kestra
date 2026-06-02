<template>
    <aside class="plugin-overview">
        <h6 class="plugin-overview__title">{{ $t("pluginPage.overview.title") }}</h6>

        <section v-if="versions?.length" class="plugin-overview__block">
            <h6 class="plugin-overview__label">{{ $t("pluginPage.overview.versions") }}</h6>

            <div class="plugin-overview__latest">
                <div class="plugin-overview__latest-row">
                    <strong class="plugin-overview__version">{{ currentVersion }}</strong>
                    <KsTag v-if="isLatest" size="small" type="success">{{ $t("pluginPage.overview.latest") }}</KsTag>
                </div>
                <span v-if="formattedDate" class="plugin-overview__meta">
                    {{ formattedDate }}
                </span>
                <a
                    v-if="releaseNotesUrl"
                    :href="releaseNotesUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="plugin-overview__link"
                >
                    {{ $t("pluginPage.overview.releaseNotes") }}
                    <OpenInNew />
                </a>
                <span v-if="minKestraVersion" class="plugin-overview__meta">
                    {{ $t("pluginPage.overview.minKestraVersion", {version: minKestraVersion}) }}
                </span>
            </div>

            <KsSelect
                v-model="selectedOlderVersion"
                size="small"
                placement="bottom-start"
                fitInputWidth
                class="plugin-overview__previous"
                :placeholder="$t('pluginPage.overview.previousVersions')"
                :disabled="otherVersions.length === 0"
                @change="onVersionPicked"
            >
                <KsOption
                    v-for="ver in otherVersions"
                    :key="ver.version"
                    :value="ver.version"
                    :label="ver.version"
                >
                    <div class="plugin-overview__version-option">
                        <div class="plugin-overview__version-option-row">
                            <span class="plugin-overview__version-option-name">{{ ver.version }}</span>
                            <OpenInNew v-if="opensExternally(ver) && ver.releaseNotesUrl" class="plugin-overview__resource-icon" />
                        </div>
                        <span v-if="formatDate(ver.publishedAt)" class="plugin-overview__version-option-meta">
                            {{ formatDate(ver.publishedAt) }}
                        </span>
                        <span v-if="ver.minKestraCompatibilityVersion" class="plugin-overview__version-option-meta">
                            {{ $t("pluginPage.overview.minKestraVersionShort", {version: ver.minKestraCompatibilityVersion}) }}
                        </span>
                    </div>
                </KsOption>
            </KsSelect>
        </section>

        <section v-if="createdBy" class="plugin-overview__block">
            <h6 class="plugin-overview__label">{{ $t("pluginPage.overview.createdBy") }}</h6>
            <div class="plugin-overview__person">
                <img
                    v-if="authorAvatar(createdBy)"
                    :src="authorAvatar(createdBy)"
                    class="plugin-overview__avatar plugin-overview__avatar--img"
                    :alt="authorName(createdBy)"
                >
                <span v-else class="plugin-overview__avatar" aria-hidden="true">{{ initial(authorName(createdBy)) }}</span>
                <span>{{ authorName(createdBy) }}</span>
            </div>
        </section>

        <section v-if="managedBy" class="plugin-overview__block">
            <h6 class="plugin-overview__label">{{ $t("pluginPage.overview.managedBy") }}</h6>
            <div class="plugin-overview__person">
                <img
                    v-if="authorAvatar(managedBy)"
                    :src="authorAvatar(managedBy)"
                    class="plugin-overview__avatar plugin-overview__avatar--img"
                    :alt="authorName(managedBy)"
                >
                <span v-else class="plugin-overview__avatar" aria-hidden="true">{{ initial(authorName(managedBy)) }}</span>
                <span>{{ authorName(managedBy) }}</span>
            </div>
        </section>

        <section v-if="categories.length > 0" class="plugin-overview__block">
            <h6 class="plugin-overview__label">{{ $t("pluginPage.overview.pluginType") }}</h6>
            <div class="plugin-overview__tags">
                <KsTag v-for="category in categories" :key="category" size="small">
                    <span class="category-label">{{ category }}</span>
                </KsTag>
            </div>
        </section>

        <section v-if="repoUrl" class="plugin-overview__block">
            <h6 class="plugin-overview__label">{{ $t("pluginPage.overview.linksResources") }}</h6>
            <a
                :href="repoUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="plugin-overview__resource"
            >
                <Github />
                <span>GitHub</span>
                <OpenInNew class="plugin-overview__resource-icon" />
            </a>
        </section>
    </aside>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import semver from "semver"
    import {KsTag, dateUtils} from "@kestra-io/design-system"
    import Github from "vue-material-design-icons/Github.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import ksLogo from "../../assets/ks-logo-small.svg"

    export interface VersionItem {
        version: string;
        publishedAt?: string | null;
        minKestraCompatibilityVersion?: string | null;
        releaseNotesUrl?: string | null;
        installed?: boolean;
    }

    export type AuthorProp = string | {name: string; avatarUrl?: string};

    const props = withDefaults(defineProps<{
        versions?: VersionItem[]
        currentVersion?: string
        isLatest?: boolean
        canSwitch?: boolean
        releaseNotesUrl?: string | null
        repoUrl?: string | null
        categories?: string[]
        createdBy?: AuthorProp | null
        managedBy?: AuthorProp | null
        currentVersionDate?: string | null
        minKestraVersion?: string | null
    }>(), {
        versions: () => [],
        currentVersion: undefined,
        isLatest: false,
        canSwitch: true,
        releaseNotesUrl: null,
        repoUrl: null,
        categories: () => [],
        createdBy: null,
        managedBy: null,
        currentVersionDate: null,
        minKestraVersion: null,
    })

    const initial = (name: string): string => {
        return (name?.trim()?.charAt(0) ?? "?").toUpperCase()
    }

    const authorName = (author: AuthorProp): string => {
        return typeof author === "string" ? author : author?.name ?? ""
    }

    const authorAvatar = (author: AuthorProp): string | undefined => {
        if (typeof author !== "string" && author?.avatarUrl) {
            return author.avatarUrl
        }
        return authorName(author).toLowerCase().includes("kestra") ? ksLogo : undefined
    }

    const formatDate = (date: string | null | undefined): string | null => {
        if (!date) return null
        const d = new Date(date)
        if (Number.isNaN(d.getTime())) return null
        return dateUtils.dateFilter(date, "LL")
    }

    const formattedDate = computed<string | null>(() => formatDate(props.currentVersionDate))

    const emit = defineEmits<{
        "select-version": [version: string]
    }>()

    const otherVersions = computed<VersionItem[]>(() => {
        const items = props.versions.filter(v => v.version !== props.currentVersion)
        return [...items].sort((a, b) => {
            const va = semver.coerce(a.version)?.version
            const vb = semver.coerce(b.version)?.version
            if (va && vb) {
                return semver.rcompare(va, vb)
            }
            return b.version.localeCompare(a.version)
        })
    })

    const selectedOlderVersion = ref<string | undefined>(undefined)

    watch(() => props.currentVersion, () => {
        selectedOlderVersion.value = undefined
    })


    const opensExternally = (ver: VersionItem): boolean => !(props.canSwitch && ver.installed)

    const onVersionPicked = (ver: string) => {
        const item = props.versions.find(v => v.version === ver)
        if (!item) {
            selectedOlderVersion.value = undefined
            return
        }
        if (opensExternally(item)) {
            if (item.releaseNotesUrl) window.open(item.releaseNotesUrl, "_blank", "noopener,noreferrer")
        } else if (ver !== props.currentVersion) {
            emit("select-version", ver)
        }
        selectedOlderVersion.value = undefined
    }

</script>

<style scoped lang="scss">
    .plugin-overview {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-5);
        margin-top: var(--ks-spacing-8);
        min-width: 0;

        &__title {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            font-weight: 700;
            letter-spacing: 0.05em;
            text-transform: uppercase;
            color: var(--ks-text-primary);
        }

        &__block {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-2);
            padding-bottom: var(--ks-spacing-4);
            border-bottom: 1px solid var(--ks-border-default);

            &:last-child {
                border-bottom: none;
                padding-bottom: 0;
            }
        }

        &__label {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        &__latest {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-1);
        }

        &__latest-row {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
        }

        &__version {
            font-size: var(--ks-font-size-md);
            font-weight: 700;
            color: var(--ks-text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            min-width: 0;
        }

        &__link {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-link);
            text-decoration: none;
            width: fit-content;

            &:hover {
                color: var(--ks-text-link);
                text-decoration: underline;
            }
        }

        &__meta {
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-secondary);
            line-height: 1.25rem;
        }

        &__previous {
            width: 100%;
        }

        &__version-option {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-px);
            padding: var(--ks-spacing-2) 0;
            line-height: 1.25rem;
        }

        &__version-option-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-2);
        }

        &__version-option-name {
            font-size: var(--ks-font-size-xs);
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        &__version-option-meta {
            font-size: var(--ks-font-size-2xs);
            color: var(--ks-text-secondary);
        }

        &__person {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-secondary);
        }

        &__avatar {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 1.5rem;
            height: 1.5rem;
            border-radius: 50%;
            background-color: var(--ks-bg-tag);
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-xs);
            font-weight: 700;
            flex-shrink: 0;

            &--img {
                background-color: transparent;
                object-fit: contain;
            }
        }

        &__tags {
            display: flex;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
        }

        &__resource {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            background-color: var(--ks-bg-tag);
            border-radius: var(--ks-radius-base);
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-xs);
            font-weight: 600;
            text-decoration: none;
            width: fit-content;

            &:hover {
                background-color: var(--ks-bg-active);
                color: var(--ks-text-primary);
            }
        }

        &__resource-icon {
            color: var(--ks-text-secondary);
        }
    }

    .category-label {
        display: inline-block;
        text-transform: lowercase;

        &::first-letter {
            text-transform: uppercase;
        }
    }
</style>
