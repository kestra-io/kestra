<template>
    <div class="plugin-doc">
        <template v-if="fetchPluginDocumentation && currentPlugin">
            <div class="dp-header">
                <div class="dp-kicker">{{ packagePath }}</div>
                <div class="dp-title-row">
                    <TaskIcon
                        class="dp-icon"
                        :cls="currentPlugin.cls"
                        onlyIcon
                        :loadIcon="pluginsStore.loadIcon"
                    />
                    <span class="dp-name text-truncate">{{ pluginName }}</span>
                    <KsTag size="small" class="dp-kind-chip">{{ pluginKind }}</KsTag>
                    <div class="dp-actions">
                        <KsButton
                            v-if="releaseNotesUrl"
                            :icon="GitHub"
                            :tooltip="$t('plugins.release')"
                            size="small"
                            link
                            @click="openReleaseNotes"
                        />
                    </div>
                </div>
                <div class="dp-type">
                    <span class="dp-cls text-truncate">{{ currentPlugin.cls }}</span>
                    <KsTag v-if="currentPlugin.version" size="small" class="dp-version">
                        {{ currentPlugin.version }}
                    </KsTag>
                    <KsTooltip
                        trigger="click"
                        :content="$t('copied')"
                        :autoClose="2000"
                        placement="top"
                    >
                        <KsButton
                            :icon="ContentCopy"
                            :aria-label="$t('plugins.copy_type')"
                            size="small"
                            link
                            @click="copyType"
                        />
                    </KsTooltip>
                </div>
                <p v-if="pluginSummary" class="dp-summary">{{ pluginSummary }}</p>
            </div>

            <nav class="dp-nav" aria-label="Documentation sections">
                <button
                    type="button"
                    v-for="chip in navChips"
                    :key="chip.id"
                    class="dp-navchip"
                    :class="{active: activeSection === chip.id}"
                    @click="selectSection(chip.id)"
                >
                    {{ chip.label }}
                    <span v-if="chip.count !== undefined" class="dp-navchip-count">{{ chip.count }}</span>
                </button>
            </nav>

            <Suspense>
                <SchemaToHtml
                    class="plugin-schema"
                    :darkMode="isDarkTheme"
                    :schema="currentPlugin?.schema"
                    :pluginType="currentPlugin?.cls"
                    :forceIncludeProperties="pluginsStore.forceIncludeProperties"
                    noUrlChange
                    compact
                    :activeSection="activeSection"
                    @section-counts="onSectionCounts"
                >
                    <template #markdown="{content}">
                        <KsMarkdown
                            :content="content"
                        />
                    </template>
                </SchemaToHtml>
            </Suspense>
        </template>

        <div v-else-if="introContent" class="dp-intro" :class="{'position-absolute': absolute}">
            <div class="dp-intro-hero">
                <div class="dp-intro-icon">
                    <BookOpenVariant :size="22" />
                </div>
                <h2 class="dp-intro-title">{{ t("plugins.intro_title") }}</h2>
                <p class="dp-intro-sub">{{ t("plugins.intro_sub") }}</p>
                <div class="dp-intro-callout">
                    <CursorPointer :size="17" class="dp-intro-callout-icon" />
                    <p>{{ t("plugins.intro_callout_prefix") }}<code>type</code>{{ t("plugins.intro_callout_suffix") }}</p>
                </div>
            </div>

            <div class="dp-intro-search-wrap">
                <KsInput
                    v-model="introSearch"
                    :placeholder="t('plugins.intro_search_placeholder')"
                    clearable
                    size="small"
                    class="dp-intro-search"
                >
                    <template #prefix>
                        <Magnify :size="15" />
                    </template>
                </KsInput>
            </div>

            <nav class="dp-nav dp-intro-nav" aria-label="Documentation sections">
                <button
                    type="button"
                    v-for="tab in introTabs"
                    :key="tab.id"
                    class="dp-navchip"
                    :class="{active: introSection === tab.id}"
                    @click="introSection = tab.id"
                >
                    {{ tab.label }}
                </button>
            </nav>

            <div class="dp-intro-body">
                <template v-if="introSection === 'overview'">
                    <div class="dp-intro-sec-label">{{ t("plugins.intro_start_here") }}</div>
                    <div class="dp-intro-qstart">
                        <button type="button" class="dp-intro-qrow" @click="introSection = 'flow'">
                            <span class="dp-intro-qnum">1</span>
                            <span class="dp-intro-qtext">
                                <span class="dp-intro-qt">{{ t("plugins.intro_step1_title") }}</span>
                                <span class="dp-intro-qd">{{ t("plugins.intro_step1_desc_prefix") }}<code>id</code>{{ t("plugins.intro_step1_desc_mid") }}<code>namespace</code>{{ t("plugins.intro_step1_desc_suffix") }}</span>
                                <span class="dp-intro-qcta">{{ t("plugins.intro_step1_cta") }}</span>
                            </span>
                            <ChevronRight :size="18" class="dp-intro-qchevron" />
                        </button>
                        <button type="button" class="dp-intro-qrow" @click="introSection = 'tasks'">
                            <span class="dp-intro-qnum">2</span>
                            <span class="dp-intro-qtext">
                                <span class="dp-intro-qt">{{ t("plugins.intro_step2_title") }}</span>
                                <span class="dp-intro-qd">{{ t("plugins.intro_step2_desc") }}</span>
                                <span class="dp-intro-qcta">{{ t("plugins.intro_step2_cta") }}</span>
                            </span>
                            <ChevronRight :size="18" class="dp-intro-qchevron" />
                        </button>
                        <button type="button" class="dp-intro-qrow" @click="introSection = 'inputs'">
                            <span class="dp-intro-qnum">3</span>
                            <span class="dp-intro-qtext">
                                <span class="dp-intro-qt">{{ t("plugins.intro_step3_title") }}</span>
                                <span class="dp-intro-qd">{{ t("plugins.intro_step3_desc_prefix") }}<code>inputs</code>{{ t("plugins.intro_step3_desc_mid") }}<code>variables</code>{{ t("plugins.intro_step3_desc_suffix") }}</span>
                                <span class="dp-intro-qcta">{{ t("plugins.intro_step3_cta") }}</span>
                            </span>
                            <ChevronRight :size="18" class="dp-intro-qchevron" />
                        </button>
                    </div>

                    <div class="dp-intro-sec-label">{{ t("plugins.intro_learn_more") }}</div>
                    <div class="dp-intro-linkgrid">
                        <a
                            v-for="link in learnMoreLinks"
                            :key="link.url"
                            :href="link.url"
                            target="_blank"
                            rel="noopener"
                            class="dp-intro-linkcard"
                        >
                            <component :is="link.icon" :size="16" class="dp-intro-linkcard-icon" />
                            <span class="dp-intro-linkcard-label">{{ link.label }}</span>
                            <OpenInNew :size="12" class="dp-intro-linkcard-ext" />
                        </a>
                    </div>
                </template>

                <template v-else-if="introSection === 'flow'">
                    <template v-if="introSearch && filteredFlowRows.length === 0">
                        <div class="dp-intro-empty">
                            <div class="dp-intro-empty-icon"><Magnify :size="22" /></div>
                            <h4>{{ t("plugins.intro_no_matches_title", {q: introSearch}) }}</h4>
                            <p>{{ t("plugins.intro_no_matches_sub") }}</p>
                            <KsButton class="dp-intro-clear-btn" size="small" @click="introSearch = ''">{{ t("plugins.intro_clear_search") }}</KsButton>
                        </div>
                    </template>
                    <KsMarkdown
                        v-else
                        class="dp-intro-md"
                        :content="filteredFlowContent"
                    />
                </template>

                <template v-else-if="introSection === 'tasks'">
                    <template v-if="introSearch && filteredTaskRows.length === 0">
                        <div class="dp-intro-empty">
                            <div class="dp-intro-empty-icon"><Magnify :size="22" /></div>
                            <h4>{{ t("plugins.intro_no_matches_title", {q: introSearch}) }}</h4>
                            <p>{{ t("plugins.intro_no_matches_sub") }}</p>
                            <KsButton class="dp-intro-clear-btn" size="small" @click="introSearch = ''">{{ t("plugins.intro_clear_search") }}</KsButton>
                        </div>
                    </template>
                    <KsMarkdown
                        v-else
                        class="dp-intro-md"
                        :content="filteredTaskContent"
                    />
                </template>

                <template v-else-if="introSection === 'inputs'">
                    <template v-if="introSearch && filteredInputRows.length === 0">
                        <div class="dp-intro-empty">
                            <div class="dp-intro-empty-icon"><Magnify :size="22" /></div>
                            <h4>{{ t("plugins.intro_no_matches_title", {q: introSearch}) }}</h4>
                            <p>{{ t("plugins.intro_no_matches_sub") }}</p>
                            <KsButton class="dp-intro-clear-btn" size="small" @click="introSearch = ''">{{ t("plugins.intro_clear_search") }}</KsButton>
                        </div>
                    </template>
                    <KsMarkdown
                        v-else
                        class="dp-intro-md"
                        :content="filteredInputContent"
                    />
                </template>

                <template v-else-if="introSection === 'pebble'">
                    <template v-if="introSearch">
                        <div v-if="filteredPebbleRows.length === 0" class="dp-intro-empty">
                            <div class="dp-intro-empty-icon"><Magnify :size="22" /></div>
                            <h4>{{ t("plugins.intro_no_matches_title", {q: introSearch}) }}</h4>
                            <p>{{ t("plugins.intro_no_matches_sub") }}</p>
                            <KsButton class="dp-intro-clear-btn" size="small" @click="introSearch = ''">{{ t("plugins.intro_clear_search") }}</KsButton>
                        </div>
                        <template v-else>
                            <p class="dp-intro-result-count">{{ t("plugins.intro_result_count", {count: filteredPebbleRows.length}) }}</p>
                            <div class="dp-intro-fn-list">
                                <div
                                    v-for="row in filteredPebbleRows"
                                    :key="row.name"
                                    class="dp-intro-fn"
                                >
                                    <span class="dp-intro-fn-name" v-html="row.nameHtml" />
                                    <div class="dp-intro-fn-desc" v-html="row.descHtml" />
                                    <code v-if="row.example" class="dp-intro-fn-ex">{{ row.example }}</code>
                                </div>
                            </div>
                        </template>
                    </template>
                    <KsMarkdown
                        v-else
                        class="dp-intro-md"
                        :content="pebbleContent"
                    />
                </template>

                <template v-else-if="introSection === 'examples'">
                    <KsMarkdown
                        class="dp-intro-md"
                        :content="examplesContent"
                    />
                </template>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import SchemaToHtml from "./schema/SchemaToHtml.vue"
    import {KsButton, KsInput, KsMarkdown, KsTag, KsTooltip} from "@kestra-io/design-system"
    import TaskIcon from "./TaskIcon.vue"
    import {getPluginReleaseUrl} from "../../utils/pluginUtils"
    import {getTheme, copy} from "../../utils/utils"
    import {useMiscStore} from "override/stores/misc"
    import {usePluginsStore} from "../../stores/plugins"
    import {useI18n} from "vue-i18n"
    import GitHub from "vue-material-design-icons/Github.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import BookOpenVariant from "vue-material-design-icons/BookOpenVariant.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import CursorPointer from "vue-material-design-icons/CursorPointer.vue"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import BookOpenPageVariant from "vue-material-design-icons/BookOpenPageVariant.vue"
    import SchoolOutline from "vue-material-design-icons/SchoolOutline.vue"
    import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline.vue"
    import Forum from "vue-material-design-icons/Forum.vue"
    import intro from "../../assets/docs/basic.md?raw"

    const FIRST_SENTENCE_REGEX = /^(.+?[.!?])(?:\s|$)/s

    const props = withDefaults(defineProps<{
        overrideIntro?: string | null;
        absolute?: boolean;
        fetchPluginDocumentation?: boolean;
        plugin?: any;
    }>(), {
        overrideIntro: null,
        absolute: false,
        fetchPluginDocumentation: true,
        plugin: null,
    })

    const {t} = useI18n()
    const miscStore = useMiscStore()
    const pluginsStore = usePluginsStore()
    const activeSection = ref("overview")
    const sectionCounts = ref<{properties?: number; outputs?: number; examples?: boolean}>({})
    const introSection = ref("overview")
    const introSearch = ref("")

    const isDarkTheme = computed(() => {
        void miscStore.theme
        return getTheme() === "dark"
    })

    const currentPlugin = computed(() => {
        return props.plugin ?? pluginsStore.editorPlugin
    })

    const introContent = computed(() => {
        return props.overrideIntro ?? intro
    })

    const pluginName = computed(() => {
        const parts = currentPlugin.value?.cls.split(".")
        return parts[parts.length - 1]
    })

    const packagePath = computed(() => {
        const parts = currentPlugin.value?.cls.split(".")
        return parts.slice(0, -1).join(".")
    })

    const pluginKind = computed(() => {
        const cls = currentPlugin.value?.cls ?? ""
        if (cls.includes(".trigger.")) return "Trigger"
        if (cls.includes(".condition.")) return "Condition"
        return "Task"
    })

    const releaseNotesUrl = computed(() => {
        return getPluginReleaseUrl(currentPlugin.value?.cls)
    })

    const pluginSummary = computed(() => {
        const description = currentPlugin.value?.schema?.properties?.description as string | undefined
        if (!description) return undefined
        const stripped = description.replace(/!\[.*?\]\(.*?\)/g, "").replace(/\[([^\]]+)\]\([^)]+\)/g, "$1").replace(/[*_`#]/g, "").trim()
        const match = stripped.match(FIRST_SENTENCE_REGEX)
        return match ? match[1].trim() : stripped.slice(0, 160)
    })

    const navChips = computed(() => {
        const chips: Array<{id: string; label: string; count?: number}> = [
            {id: "overview", label: t("plugins.nav_overview")},
        ]
        if (sectionCounts.value.properties !== undefined) {
            chips.push({id: "properties", label: t("plugins.nav_properties"), count: sectionCounts.value.properties})
        }
        if (sectionCounts.value.outputs !== undefined) {
            chips.push({id: "outputs", label: t("plugins.nav_outputs"), count: sectionCounts.value.outputs})
        }
        if (sectionCounts.value.examples) {
            chips.push({id: "examples", label: t("plugins.nav_examples")})
        }
        return chips
    })

    const onSectionCounts = (counts: {properties?: number; outputs?: number; examples?: boolean}) => {
        sectionCounts.value = counts
    }

    watch(currentPlugin, () => {
        activeSection.value = "overview"
        sectionCounts.value = {}
    })

    const selectSection = (id: string) => {
        activeSection.value = id
    }

    const openReleaseNotes = () => {
        if (releaseNotesUrl.value) {
            window.open(releaseNotesUrl.value, "_blank")
        }
    }

    const copyType = async () => {
        await copy(currentPlugin.value?.cls ?? "")
    }

    const introTabs = computed(() => [
        {id: "overview", label: t("plugins.intro_tab_overview")},
        {id: "flow", label: t("plugins.intro_tab_flow")},
        {id: "tasks", label: t("plugins.intro_tab_tasks")},
        {id: "inputs", label: t("plugins.intro_tab_inputs")},
        {id: "pebble", label: t("plugins.intro_tab_pebble")},
        {id: "examples", label: t("plugins.intro_tab_examples")},
    ])

    const mdSections = computed<Record<string, string>>(() => {
        const src = introContent.value
        const parts = src.split(/^(?=### )/m)
        const map: Record<string, string> = {}
        for (const part of parts) {
            const headingMatch = part.match(/^### (.+)\n/)
            if (headingMatch) {
                map[headingMatch[1].trim()] = part.trim()
            }
        }
        return map
    })

    const flowContent = computed(() => {
        const sections = ["Flow properties", "Plugin documentation"]
        return sections.map(k => mdSections.value[k] ?? "").filter(Boolean).join("\n\n")
    })

    const taskContent = computed(() => mdSections.value["Task properties"] ?? "")

    const inputContent = computed(() => {
        const sections = ["Input types", "Input properties"]
        return sections.map(k => mdSections.value[k] ?? "").filter(Boolean).join("\n\n")
    })

    const pebbleContent = computed(() => {
        const sections = ["Pebble templating", "Pebble functions, filters and tags", "Common Pebble expressions"]
        return sections.map(k => mdSections.value[k] ?? "").filter(Boolean).join("\n\n")
    })

    const examplesContent = computed(() => mdSections.value["Flow example"] ?? "")

    const learnMoreLinks = computed(() => {
        const raw = mdSections.value["Links to learn more"] ?? ""
        const links: Array<{label: string; url: string; icon: object}> = []
        const iconMap: Record<string, object> = {
            "tutorial": SchoolOutline,
            "youtube": PlayCircleOutline,
            "watch": PlayCircleOutline,
            "slack": Forum,
            "community": Forum,
            "github": GitHub,
        }
        for (const line of raw.split("\n")) {
            const match = line.match(/\[([^\]]+)\]\(([^)]+)\)/)
            if (!match) continue
            const label = match[1].trim()
            const url = match[2].trim()
            const key = Object.keys(iconMap).find(k => label.toLowerCase().includes(k) || url.toLowerCase().includes(k))
            links.push({label, url, icon: key ? iconMap[key] : BookOpenPageVariant})
        }
        return links
    })

    interface PebbleRow {
        name: string;
        nameHtml: string;
        desc: string;
        descHtml: string;
        example: string;
    }

    const pebbleRows = computed<PebbleRow[]>(() => {
        const rows: PebbleRow[] = []
        const src = pebbleContent.value
        for (const line of src.split("\n")) {
            const match = line.match(/^\|\s*`([^`]+)`\s*\|\s*(.+?)\s*\|?\s*$/)
            if (!match) continue
            const name = match[1].trim()
            const rest = match[2].trim()
            const exMatch = rest.match(/`([^`]+)`\s*(?:—|-)/)
            const example = exMatch ? exMatch[1] : ""
            const desc = rest.replace(/`[^`]+`\s*(?:—|-)\s*/, "").replace(/\\\|/g, "|").trim()
            rows.push({name, nameHtml: name, desc, descHtml: desc, example})
        }
        return rows
    })

    const filteredPebbleRows = computed<PebbleRow[]>(() => {
        const q = introSearch.value.trim().toLowerCase()
        if (!q) return pebbleRows.value
        const esc = q.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
        const re = new RegExp(`(${esc})`, "gi")
        return pebbleRows.value
            .filter(r => r.name.toLowerCase().includes(q) || r.desc.toLowerCase().includes(q) || r.example.toLowerCase().includes(q))
            .map(r => ({
                ...r,
                nameHtml: r.name.replace(re, "<mark class=\"dp-intro-hl\">$1</mark>"),
                descHtml: r.desc.replace(re, "<mark class=\"dp-intro-hl\">$1</mark>"),
            }))
    })

    function filterTableRows(content: string, q: string): string[] {
        const rows: string[] = []
        for (const line of content.split("\n")) {
            if (/^\|[-\s|]+\|/.test(line)) continue
            if (line.startsWith("| ")) {
                rows.push(line)
            }
        }
        if (!q) return rows
        return rows.filter(r => r.toLowerCase().includes(q.toLowerCase()))
    }

    const filteredFlowRows = computed(() => filterTableRows(flowContent.value, introSearch.value))
    const filteredTaskRows = computed(() => filterTableRows(taskContent.value, introSearch.value))
    const filteredInputRows = computed(() => filterTableRows(inputContent.value, introSearch.value))

    const filteredFlowContent = computed(() => {
        const q = introSearch.value.trim()
        if (!q) return flowContent.value
        return buildFilteredContent(flowContent.value, q)
    })

    const filteredTaskContent = computed(() => {
        const q = introSearch.value.trim()
        if (!q) return taskContent.value
        return buildFilteredContent(taskContent.value, q)
    })

    const filteredInputContent = computed(() => {
        const q = introSearch.value.trim()
        if (!q) return inputContent.value
        return buildFilteredContent(inputContent.value, q)
    })

    function buildFilteredContent(content: string, q: string): string {
        const lower = q.toLowerCase()
        const lines = content.split("\n")
        const out: string[] = []
        let pastHeader = false

        for (const line of lines) {
            const isSep = /^\|[-\s|:]+\|/.test(line)
            const isRow = line.startsWith("| ")

            if (isSep) {
                pastHeader = true
                out.push(line)
                continue
            }

            if (isRow) {
                if (!pastHeader) {
                    out.push(line)
                    continue
                }
                if (line.toLowerCase().includes(lower)) {
                    out.push(line)
                }
                continue
            }

            pastHeader = false
            out.push(line)
        }

        return out.join("\n")
    }
</script>

<style scoped lang="scss">
  .plugin-doc {
    --ks-table-row-hover-bg: transparent;
  }

  .plugin-schema {
    display: block;
    padding: var(--ks-spacing-5) var(--ks-spacing-4) var(--ks-spacing-6);
  }

  .dp-header {
    position: sticky;
    top: 0;
    z-index: 5;
    background: var(--ks-bg-surface);
    padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-3);
    border-bottom: 1px solid var(--ks-border-default);
    box-shadow: var(--ks-shadow-sm);
  }

  .dp-kicker {
    font-size: var(--ks-font-size-2xs);
    font-weight: var(--ks-font-weight-semibold);
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--ks-text-muted);
    margin-bottom: var(--ks-spacing-2);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .dp-title-row {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
  }

  .dp-icon {
    width: var(--ks-icon-size-lg);
    height: var(--ks-icon-size-lg);
    min-width: var(--ks-icon-size-lg);
    flex: none;
  }

  .dp-name {
    font-size: var(--ks-font-size-lg);
    font-weight: var(--ks-font-weight-bold);
    color: var(--ks-text-primary);
    min-width: 0;
    flex: 1;
  }

  .dp-kind-chip {
    flex: none;
  }

  .dp-actions {
    display: flex;
    gap: var(--ks-spacing-1);
    flex: none;
  }

  .dp-type {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    margin-top: var(--ks-spacing-3);
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    padding: var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-3);
  }

  .dp-cls {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-dim);
    flex: 1;
    min-width: 0;
  }

  .dp-version {
    flex: none;
    font-family: var(--ks-font-family-mono);
  }

  .dp-summary {
    margin: var(--ks-spacing-3) 0 0;
    font-size: var(--ks-font-size-sm);
    line-height: 1.55;
    color: var(--ks-text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .dp-nav {
    position: sticky;
    top: 0;
    z-index: 4;
    display: flex;
    gap: var(--ks-spacing-1);
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    background: var(--ks-bg-surface);
    border-bottom: 1px solid var(--ks-border-default);
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .dp-navchip {
    flex: none;
    padding: var(--ks-spacing-1) var(--ks-spacing-3);
    border-radius: var(--ks-radius-sm);
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-medium);
    color: var(--ks-text-muted);
    cursor: pointer;
    white-space: nowrap;
    background: transparent;
    border: 1px solid transparent;
    font-family: var(--ks-font-family-sans);
    transition: color var(--ks-duration-fast) ease, background var(--ks-duration-fast) ease;

    &:hover {
      color: var(--ks-text-primary);
      background: var(--ks-bg-hover);
    }

    &.active {
      color: var(--ks-text-link);
      background: var(--ks-bg-tag-active);
    }
  }

  .dp-navchip-count {
    opacity: 0.65;
    margin-left: var(--ks-spacing-1);
    font-variant-numeric: tabular-nums;
  }

  .dp-intro {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .dp-intro-hero {
    padding: var(--ks-spacing-5) var(--ks-spacing-4) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
  }

  .dp-intro-icon {
    width: 38px;
    height: 38px;
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-tag-active);
    display: grid;
    place-items: center;
    margin-bottom: var(--ks-spacing-3);
    color: var(--ks-text-link);
  }

  .dp-intro-title {
    font-size: var(--ks-font-size-xl);
    font-weight: var(--ks-font-weight-bold);
    margin: 0 0 var(--ks-spacing-1);
    letter-spacing: -0.01em;
    color: var(--ks-text-primary);
  }

  .dp-intro-sub {
    font-size: var(--ks-font-size-sm);
    line-height: 1.55;
    color: var(--ks-text-secondary);
    margin: 0;
  }

  .dp-intro-callout {
    display: flex;
    gap: var(--ks-spacing-2);
    align-items: flex-start;
    margin-top: var(--ks-spacing-4);
    padding: var(--ks-spacing-3);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-info);
    border: 1px solid var(--ks-border-info, color-mix(in srgb, var(--ks-status-info) 30%, transparent));

    p {
      margin: 0;
      font-size: var(--ks-font-size-sm);
      line-height: 1.5;
      color: var(--ks-text-primary);
    }

    code {
      font-family: var(--ks-font-family-mono);
      font-size: 0.9em;
      background: var(--ks-bg-surface);
      padding: 1px 5px;
      border-radius: var(--ks-radius-xs);
    }
  }

  .dp-intro-callout-icon {
    color: var(--ks-status-info);
    flex: none;
    margin-top: 1px;
  }

  .dp-intro-search-wrap {
    padding: var(--ks-spacing-3) var(--ks-spacing-4) 0;
  }

  .dp-intro-search {
    width: 100%;
  }

  .dp-intro-nav {
    top: 0;
  }

  .dp-intro-body {
    flex: 1;
    overflow-y: auto;
    padding: var(--ks-spacing-5) var(--ks-spacing-4) var(--ks-spacing-6);
    scrollbar-width: thin;
    scrollbar-color: var(--ks-border-strong) transparent;
  }

  .dp-intro-sec-label {
    font-size: var(--ks-font-size-xs);
    font-weight: var(--ks-font-weight-bold);
    letter-spacing: 0.07em;
    text-transform: uppercase;
    color: var(--ks-text-muted);
    margin: 0 0 var(--ks-spacing-3);

    & + .dp-intro-sec-label {
      margin-top: var(--ks-spacing-5);
    }
  }

  .dp-intro-qstart + .dp-intro-sec-label {
    margin-top: var(--ks-spacing-6);
  }

  .dp-intro-qstart {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
  }

  .dp-intro-qrow {
    display: flex;
    gap: var(--ks-spacing-3);
    align-items: center;
    width: 100%;
    text-align: left;
    font: inherit;
    background: none;
    padding: var(--ks-spacing-4);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    cursor: pointer;
    transition: border-color var(--ks-duration-fast) ease;

    &:hover {
      border-color: var(--ks-border-strong);
    }

    &:hover .dp-intro-qt {
      color: var(--ks-text-link);
    }

    &:hover .dp-intro-qchevron {
      color: var(--ks-text-link);
      transform: translateX(2px);
    }
  }

  .dp-intro-qnum {
    width: 20px;
    height: 20px;
    flex: none;
    align-self: flex-start;
    border-radius: 999px;
    background: var(--ks-bg-tag-active);
    color: var(--ks-text-link);
    display: grid;
    place-items: center;
    font-size: var(--ks-font-size-2xs);
    font-weight: var(--ks-font-weight-bold);
  }

  .dp-intro-qtext {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
  }

  .dp-intro-qt {
    display: block;
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-semibold);
    color: var(--ks-text-primary);
    margin: 0 0 2px;
    transition: color var(--ks-duration-fast) ease;
  }

  .dp-intro-qd {
    display: block;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
    margin: 0;
    line-height: 1.45;

    code {
      font-family: var(--ks-font-family-mono);
      font-size: 0.9em;
      background: var(--ks-bg-tag);
      padding: 1px 5px;
      border-radius: var(--ks-radius-xs);
      color: var(--ks-text-dim);
    }
  }

  .dp-intro-qcta {
    display: block;
    margin-top: var(--ks-spacing-2);
    font-size: var(--ks-font-size-xs);
    font-weight: var(--ks-font-weight-medium);
    color: var(--ks-text-link);
  }

  .dp-intro-qchevron {
    flex: none;
    align-self: center;
    color: var(--ks-icon-muted);
    transition: color var(--ks-duration-fast) ease, transform var(--ks-duration-fast) ease;
  }

  .dp-intro-linkgrid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--ks-spacing-3);
    margin-top: var(--ks-spacing-4);
  }

  .dp-intro-linkcard {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-3);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    cursor: pointer;
    text-decoration: none;
    transition: background var(--ks-duration-fast) ease, border-color var(--ks-duration-fast) ease, transform var(--ks-duration-fast) ease;

    &:hover {
      background: var(--ks-bg-hover);
      border-color: var(--ks-border-strong);
      transform: translateY(-1px);
    }
  }

  .dp-intro-linkcard-label {
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-medium);
    color: var(--ks-text-primary);
    flex: 1;
    min-width: 0;
  }

  .dp-intro-linkcard-icon {
    color: var(--ks-icon-default);
    flex: none;
  }

  .dp-intro-linkcard-ext {
    color: var(--ks-text-muted);
    flex: none;
    margin-left: auto;
  }

  .dp-intro-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: var(--ks-spacing-8) var(--ks-spacing-5);
    gap: var(--ks-spacing-1);
  }

  .dp-intro-empty-icon {
    width: 48px;
    height: 48px;
    border-radius: var(--ks-radius-lg);
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    display: grid;
    place-items: center;
    margin-bottom: var(--ks-spacing-2);
    color: var(--ks-icon-default);
  }

  .dp-intro-empty h4 {
    font-size: var(--ks-font-size-base);
    font-weight: var(--ks-font-weight-semibold);
    margin: 0;
    color: var(--ks-text-primary);
  }

  .dp-intro-empty p {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-muted);
    margin: 0;
    line-height: 1.5;
    max-width: 240px;
  }

  .dp-intro-clear-btn {
    margin-top: var(--ks-spacing-3);
  }

  .dp-intro-result-count {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-muted);
    margin: 0 0 var(--ks-spacing-2);
  }

  .dp-intro-fn-list {
    display: flex;
    flex-direction: column;
  }

  .dp-intro-fn {
    padding: var(--ks-spacing-4) 0;
    border-top: 1px solid var(--ks-border-default);

    &:first-of-type {
      border-top: none;
      padding-top: 0;
    }
  }

  .dp-intro-fn-name {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-semibold);
    color: var(--ks-text-link);
  }

  .dp-intro-fn-desc {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
    margin-top: 3px;
    line-height: 1.45;
  }

  .dp-intro-fn-ex {
    display: block;
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-dim);
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-xs);
    padding: var(--ks-spacing-1) var(--ks-spacing-2);
    margin-top: var(--ks-spacing-1);
    white-space: pre-wrap;
    overflow-x: auto;
  }

  .dp-intro-md :deep(h1),
  .dp-intro-md :deep(h2),
  .dp-intro-md :deep(h3) {
    font-size: var(--ks-font-size-2xs);
    font-weight: var(--ks-font-weight-bold);
    text-transform: uppercase;
    letter-spacing: 0.07em;
    color: var(--ks-text-muted);
    margin: var(--ks-spacing-6) 0 var(--ks-spacing-3);
  }

  .dp-intro-md :deep(> *:first-child),
  .dp-intro-md :deep(h3:first-child) {
    margin-top: 0;
  }

  .dp-intro-md :deep(h3:first-of-type) {
    font-size: var(--ks-font-size-xl);
    font-weight: var(--ks-font-weight-bold);
    text-transform: none;
    letter-spacing: -0.01em;
    color: var(--ks-text-primary);
    margin-top: 0;
  }

  .dp-intro-md :deep(p),
  .dp-intro-md :deep(li) {
    font-size: var(--ks-font-size-base);
    line-height: var(--ks-line-height-base);
    color: var(--ks-text-secondary);
  }

  .dp-intro-md :deep(p) {
    margin: 0 0 var(--ks-spacing-3);
  }

  .dp-intro-md :deep(table) {
    display: block;
    width: 100%;
    margin: 0 0 var(--ks-spacing-4);
  }

  .dp-intro-md :deep(thead) {
    display: none;
  }

  .dp-intro-md :deep(tbody) {
    display: block;
  }

  .dp-intro-md :deep(tr) {
    display: block;
    padding: var(--ks-spacing-3) 0;
    border-top: 1px solid var(--ks-border-subtle);
  }

  .dp-intro-md :deep(tr:first-child) {
    border-top: none;
    padding-top: 0;
  }

  .dp-intro-md :deep(td) {
    display: block;
    padding: 0;
    border: none;
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-base);
    line-height: var(--ks-line-height-base);
  }

  .dp-intro-md :deep(td:first-child) {
    margin-bottom: var(--ks-spacing-2);
  }

  .dp-intro-md :deep(code) {
    font-family: var(--ks-font-family-mono);
    font-size: 0.9em;
    background: var(--ks-bg-tag);
    color: var(--ks-text-primary);
    padding: 1px 6px;
    border-radius: var(--ks-radius-xs);
  }

  .dp-intro-md :deep(a) {
    color: var(--ks-text-link);
    text-decoration: none;
  }

  .dp-intro-md :deep(a:hover) {
    text-decoration: underline;
  }

  .dp-intro-md :deep(pre) {
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    padding: var(--ks-spacing-3);
    overflow-x: auto;
  }

  .dp-intro-md :deep(pre code) {
    background: none;
    padding: 0;
    font-size: var(--ks-font-size-xs);
  }

  :deep(.dp-intro-hl) {
    background: color-mix(in srgb, var(--ks-primary-500) 18%, transparent);
    border-radius: 3px;
    padding: 0 2px;
    color: inherit;
    font-style: normal;
  }
</style>
