<template>
    <div class="enhanced-documentation">
        <div v-if="showSearch" class="doc-toolbar">
            <el-input
                v-model="searchQuery"
                :suffixIcon="Magnify"
                :clearable="true"
                class="doc-search"
                size="small"
                :placeholder="$t ? $t('search_docs') : 'Search docs...'"
            />
            <transition name="fade">
                <span v-if="!hasMatches && searchQuery" class="doc-search__empty">
                    No matches found
                </span>
            </transition>
        </div>

        <div
            ref="markdownContainer"
            :class="['markdown', 'enhanced-markdown']"
            v-html="markdownHtml"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, nextTick, onBeforeUnmount, computed} from "vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import * as Markdown from "../../utils/markdown";

    /**
     * Unified component props
     */
    const props = withDefaults(
        defineProps<{
            source?: string;
            permalink?: boolean;
            fontSizeVar?: string;
            html?: boolean;
            showSearch?: boolean;
            collapseExamples?: boolean;
            variant?: "default" | "enhanced";
        }>(),
        {
            source: "",
            permalink: false,
            fontSizeVar: "font-size-sm",
            html: true,
            showSearch: false, // good default for OSS docs
            collapseExamples: false,
            variant: "enhanced",
        }
    );

    /**
     * Reactive state
     */
    const markdownHtml = ref<string>("");
    const searchQuery = ref("");
    const markdownContainer = ref<HTMLElement | null>(null);
    const cleanups: Array<() => void> = [];
    const hasMatchesRef = ref(true);

    /**
     * Derived values
     */
    const fontSizeCss = computed(() => `var(--${props.fontSizeVar})`);
    const hasMatches = computed(() => hasMatchesRef.value);

    /**
     * RENDERING
     */
    watch(
        () => props.source,
        async () => {
            await renderMarkdown();
        },
        {immediate: true}
    );

    watch(searchQuery, () => {
        applySearchFilter();
    });

    onBeforeUnmount(() => {
        cleanupEnhancements();
    });

    /**
     * Main render function
     */
    async function renderMarkdown() {
        if (typeof window === "undefined") {
            markdownHtml.value = props.source ?? "";
            return;
        }

        markdownHtml.value = await Markdown.render(props.source, {
            permalink: props.permalink,
            html: props.html,
            variant: props.variant,
        });

        await nextTick();
        enhanceContent();
        applySearchFilter();
    }

    /**
     * Enhancement orchestration
     */
    function enhanceContent() {
        cleanupEnhancements();
        const root = markdownContainer.value;
        if (!root) return;

        transformTables(root);
        setupCardToggles(root);
        setupCollapsibles(root);
        setupFlowExampleToggle(root);
        setupCopyButtons(root);
        setupSmoothScrolling(root);
    }

    /**
     * Remove any bound listeners / timeouts etc.
     */
    function cleanupEnhancements() {
        while (cleanups.length) {
            const dispose = cleanups.pop();
            if (dispose) dispose();
        }
    }

    /* -------------------------
   Table -> Card Transformation
   ------------------------- */
    function transformTables(root: HTMLElement) {
        const tables = Array.from(root.querySelectorAll<HTMLTableElement>("table"));
        const labelsToSkip = new Set(["description", "details"]);

        tables.forEach((table) => {
            const headerCells: string[] = [];
            const headerRow = table.tHead?.rows?.[0] ?? table.rows[0];

            if (headerRow) {
                Array.from(headerRow.cells).forEach((cell) =>
                    headerCells.push(cell.textContent?.trim() ?? "")
                );
            }

            const bodyRows = table.tBodies.length
                ? Array.from(table.tBodies).flatMap((body) => Array.from(body.rows))
                : Array.from(table.rows).slice(headerRow ? 1 : 0);

            const cardGrid = document.createElement("div");
            cardGrid.classList.add("doc-card-grid");
            cardGrid.setAttribute("role", "list");

            let autoId = 0;

            bodyRows.forEach((row) => {
                const cells = Array.from(row.cells);
                if (!cells.length) return;

                const [firstCell, ...rest] = cells;
                const hasBody = rest.length > 0;

                const card = document.createElement("article");
                card.classList.add("doc-card");
                card.setAttribute("role", "listitem");

                if (hasBody) {
                    const toggle = document.createElement("button");
                    toggle.type = "button";
                    toggle.classList.add("doc-card__header");
                    toggle.innerHTML = `<span class="doc-card__title">${firstCell.innerHTML}</span><span class="doc-card__indicator" aria-hidden="true"></span>`;
                    toggle.dataset.cardToggle = "true";
                    card.appendChild(toggle);

                    const body = document.createElement("div");
                    const bodyId = `doc-card-body-${autoId++}`;
                    body.id = bodyId;
                    body.classList.add("doc-card__body");
                    body.setAttribute("aria-hidden", "true");
                    toggle.setAttribute("aria-controls", bodyId);
                    toggle.setAttribute("aria-expanded", "false");

                    rest.forEach((cell, index) => {
                        const item = document.createElement("div");
                        item.classList.add("doc-card__item");

                        const labelText = (headerCells[index + 1] ?? "").trim();
                        if (labelText && !labelsToSkip.has(labelText.toLowerCase())) {
                            const label = document.createElement("span");
                            label.classList.add("doc-card__label");
                            label.textContent = labelText;
                            item.appendChild(label);
                        }

                        const value = document.createElement("div");
                        value.classList.add("doc-card__value");
                        value.innerHTML = cell.innerHTML;
                        item.appendChild(value);
                        body.appendChild(item);
                    });

                    card.appendChild(body);
                    card.classList.add("is-collapsible", "is-collapsed");
                } else {
                    const header = document.createElement("div");
                    header.classList.add("doc-card__header", "doc-card__header--static");
                    header.innerHTML = firstCell.innerHTML;
                    card.appendChild(header);
                }

                card.dataset.searchValue = normalizeText(card.textContent ?? "");
                cardGrid.appendChild(card);
            });

            if (cardGrid.childElementCount > 0) {
                table.replaceWith(cardGrid);
            }
        });
    }

    /* -------------------------
   Card toggles
   ------------------------- */
    function setCardExpanded(card: HTMLElement, expanded: boolean) {
        if (!card.classList.contains("is-collapsible")) return;

        card.classList.toggle("is-open", expanded);
        card.classList.toggle("is-collapsed", !expanded);

        const toggle = card.querySelector<HTMLElement>(".doc-card__header[data-card-toggle='true']");
        const body = card.querySelector<HTMLElement>(".doc-card__body");

        if (toggle) toggle.setAttribute("aria-expanded", String(expanded));
        if (body) body.setAttribute("aria-hidden", String(!expanded));
    }

    function setupCardToggles(root: HTMLElement) {
        const cards = Array.from(root.querySelectorAll<HTMLElement>(".doc-card.is-collapsible"));

        cards.forEach((card) => {
            const toggle = card.querySelector<HTMLButtonElement>(".doc-card__header[data-card-toggle='true']");
            const body = card.querySelector<HTMLElement>(".doc-card__body");

            if (!toggle || !body || toggle.dataset.toggleBound === "true") return;

            const onToggle = () => {
                const expanded = !card.classList.contains("is-open");
                setCardExpanded(card, expanded);
            };

            setCardExpanded(card, false);
            toggle.dataset.toggleBound = "true";
            toggle.addEventListener("click", onToggle);

            cleanups.push(() => toggle.removeEventListener("click", onToggle));
        });
    }

    /* -------------------------
   Collapsibles & Sections
   ------------------------- */
    function setupCollapsibles(root: HTMLElement) {
        const headings = Array.from(root.querySelectorAll<HTMLElement>("h2, h3"));
        headings.forEach((heading) => {
            if (heading.dataset.enhanced === "true") return;

            const level = Number(heading.tagName.substring(1));
            heading.dataset.enhanced = "true";

            if (level === 3) {
                createCollapsibleFromHeading(heading, level);
            } else if (level === 2) {
                wrapSection(heading, level);
            }
        });
    }

    function wrapSection(heading: HTMLElement, level: number) {
        const parent = heading.parentElement;
        if (!parent) return;

        const section = document.createElement("section");
        section.classList.add("doc-section", `doc-section--level-${level}`);
        parent.insertBefore(section, heading);
        section.appendChild(heading);

        let sibling: Node | null = section.nextSibling;
        while (sibling) {
            if (isBlockingHeading(sibling, level)) break;
            const next = sibling.nextSibling;
            section.appendChild(sibling);
            sibling = next;
        }
    }

    function createCollapsibleFromHeading(heading: HTMLElement, level: number) {
        const parent = heading.parentElement;
        if (!parent) return;

        const siblingsToMove: Node[] = [];
        let walker: Node | null = heading.nextSibling;
        while (walker) {
            if (isBlockingHeading(walker, level)) break;
            siblingsToMove.push(walker);
            walker = walker.nextSibling;
        }

        const details = document.createElement("details");
        details.classList.add("doc-collapsible");
        details.open = true;

        const summary = document.createElement("summary");
        summary.classList.add("doc-collapsible__summary");

        parent.insertBefore(details, heading);
        summary.appendChild(heading);
        details.appendChild(summary);

        const body = document.createElement("div");
        body.classList.add("doc-collapsible__content");
        siblingsToMove.forEach((node) => body.appendChild(node));
        details.appendChild(body);

        cleanups.push(() => {
            details.open = true;
        });
    }

    function isBlockingHeading(node: Node | null, level: number) {
        if (!node || !(node instanceof HTMLElement)) return false;
        if (!/^H[1-6]$/.test(node.tagName)) return false;
        return Number(node.tagName.substring(1)) <= level;
    }

    /* -------------------------
   Flow example toggle
   ------------------------- */
    function setupFlowExampleToggle(root: HTMLElement) {
        if (!props.collapseExamples) return;

        const existingToggle = root.querySelector<HTMLElement>(".doc-example");
        if (existingToggle) return;

        const exampleHeading = root.querySelector<HTMLElement>("#flow-example");
        if (!exampleHeading) return;

        const contentContainer =
            exampleHeading.closest<HTMLElement>("details.doc-collapsible")?.querySelector<HTMLElement>(".doc-collapsible__content") ??
            exampleHeading.parentElement;
        if (!contentContainer) return;

        const codeBlocks = Array.from(contentContainer.querySelectorAll<HTMLElement>(".doc-code-block"));
        if (!codeBlocks.length) return;

        const wrapper = document.createElement("div");
        wrapper.classList.add("doc-example");

        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.classList.add("doc-example__toggle");

        const label = document.createElement("span");
        label.classList.add("doc-example__label");
        toggle.appendChild(label);

        const indicator = document.createElement("span");
        indicator.classList.add("doc-example__indicator");
        indicator.setAttribute("aria-hidden", "true");
        toggle.appendChild(indicator);

        const content = document.createElement("div");
        content.classList.add("doc-example__content");
        content.setAttribute("aria-hidden", "true");

        wrapper.append(toggle, content);

        const firstBlock = codeBlocks[0];
        contentContainer.insertBefore(wrapper, firstBlock);
        codeBlocks.forEach((block) => content.appendChild(block));

        const title = exampleHeading.textContent?.trim() ?? "Flow example";
        const containsExample = title.toLowerCase().includes("example");
        const baseLabel = containsExample ? title : `${title} example`;
        const contentId = `doc-example-${Math.random().toString(36).slice(2, 8)}`;
        content.id = contentId;
        toggle.setAttribute("aria-controls", contentId);

        const setExpanded = (expanded: boolean) => {
            const action = expanded ? "Hide" : "Show";
            label.textContent = `${action} ${baseLabel}`;
            toggle.setAttribute("aria-expanded", String(expanded));
            content.setAttribute("aria-hidden", String(!expanded));

            if (expanded) {
                wrapper.classList.add("is-open");
                content.style.maxHeight = `${content.scrollHeight}px`;
                const onTransitionEnd = () => {
                    if (wrapper.classList.contains("is-open")) content.style.maxHeight = "none";
                    content.removeEventListener("transitionend", onTransitionEnd);
                };
                content.addEventListener("transitionend", onTransitionEnd, {once: true});
            } else {
                wrapper.classList.remove("is-open");
                if (content.style.maxHeight === "none" || content.style.maxHeight === "") {
                    content.style.maxHeight = `${content.scrollHeight}px`;
                }
                requestAnimationFrame(() => {
                    content.style.maxHeight = "0px";
                });
            }
        };

        setExpanded(false);
        content.style.maxHeight = "0px";

        const onToggle = () => {
            const expanded = toggle.getAttribute("aria-expanded") !== "true";
            setExpanded(expanded);
        };

        toggle.addEventListener("click", onToggle);
        cleanups.push(() => toggle.removeEventListener("click", onToggle));
    }

    /* -------------------------
   Copy buttons for code blocks
   ------------------------- */
    function setupCopyButtons(root: HTMLElement) {
        const buttons = Array.from(root.querySelectorAll<HTMLButtonElement>(".doc-copy-button"));
        buttons.forEach((button) => {
            if (button.dataset.bindAttached === "true") return;

            const handler = async () => {
                const targetId = button.dataset.copyTarget;
                if (!targetId) return;

                const selector = `#${escapeSelector(targetId)}`;
                const pre = root.querySelector<HTMLElement>(selector);
                if (!pre) return;

                const text = pre.textContent ?? "";
                const result = await copyToClipboard(text);
                button.classList.toggle("copied", result);

                const label = button.querySelector<HTMLElement>(".doc-copy-label");
                if (label) {
                    if (result) {
                        const original = label.textContent ?? "";
                        label.textContent = "Copied";
                        window.setTimeout(() => {
                            label.textContent = original || "Copy";
                            button.classList.remove("copied");
                        }, 2000);
                    } else {
                        label.textContent = "Copy";
                    }
                }
            };

            button.dataset.bindAttached = "true";
            button.addEventListener("click", handler);
            cleanups.push(() => button.removeEventListener("click", handler));
        });
    }

    async function copyToClipboard(text: string) {
        if (!text) return false;
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch {
            try {
                const textarea = document.createElement("textarea");
                textarea.value = text;
                textarea.style.position = "fixed";
                textarea.style.opacity = "0";
                document.body.appendChild(textarea);
                textarea.focus();
                textarea.select();
                const successful = document.execCommand("copy");
                document.body.removeChild(textarea);
                return successful;
            } catch {
                return false;
            }
        }
    }

    /* -------------------------
   Smooth scrolling for anchors
   ------------------------- */
    function setupSmoothScrolling(root: HTMLElement) {
        const handler = (event: Event) => {
            const target = event.target as HTMLElement | null;
            if (!target) return;

            const anchor = target.closest<HTMLAnchorElement>("a[href^='#']");
            if (!anchor || anchor.target === "_blank" || anchor.hasAttribute("data-no-smooth")) return;

            const href = anchor.getAttribute("href") ?? "";
            const id = href.replace(/^#/, "");
            if (!id) return;

            const destination = document.getElementById(id);
            if (!destination) return;

            event.preventDefault();
            destination.scrollIntoView({behavior: "smooth", block: "start"});
            history.replaceState(null, "", `#${id}`);
        };

        root.addEventListener("click", handler);
        cleanups.push(() => root.removeEventListener("click", handler));
    }

    /* -------------------------
   Search filter
   ------------------------- */
    function applySearchFilter() {
        const root = markdownContainer.value;
        if (!root) return;

        const query = normalizeText(searchQuery.value);
        const cards = Array.from(root.querySelectorAll<HTMLElement>(".doc-card"));

        if (!cards.length) {
            hasMatchesRef.value = query.length === 0;
            return;
        }

        if (!query) {
            cards.forEach((card) => {
                card.classList.remove("is-hidden");
                setCardExpanded(card, false);
            });

            root.querySelectorAll<HTMLDetailsElement>("details.doc-collapsible").forEach((detail) => {
                detail.classList.remove("is-dimmed");
                detail.open = true;
            });

            hasMatchesRef.value = true;
            return;
        }

        let matches = 0;
        cards.forEach((card) => {
            const cardText = card.dataset.searchValue ?? normalizeText(card.textContent ?? "");
            const matched = cardText.includes(query);
            card.classList.toggle("is-hidden", !matched);
            if (matched) {
                matches += 1;
                setCardExpanded(card, true);
                const container = card.closest<HTMLDetailsElement>("details.doc-collapsible");
                if (container) {
                    container.open = true;
                    container.classList.remove("is-dimmed");
                }
            } else {
                setCardExpanded(card, false);
            }
        });

        root.querySelectorAll<HTMLDetailsElement>("details.doc-collapsible").forEach((detail) => {
            const hasVisibleCard = Array.from(detail.querySelectorAll<HTMLElement>(".doc-card")).some(
                (card) => !card.classList.contains("is-hidden")
            );
            detail.classList.toggle("is-dimmed", !hasVisibleCard);
            if (!hasVisibleCard) {
                detail.open = false;
            }
        });

        hasMatchesRef.value = matches > 0;
    }

    /* -------------------------
   Helpers
   ------------------------- */
    function normalizeText(value: string) {
        return value.toLowerCase().replace(/\s+/g, " ").trim();
    }

    function escapeSelector(value: string) {
        if (typeof window !== "undefined" && (window as any).CSS && typeof (window as any).CSS.escape === "function") {
            return (window as any).CSS.escape(value);
        }
        return value.replace(/[^a-zA-Z0-9_-]/g, (char) => `\\${char}`);
    }
</script>

<style scoped lang="scss">
.enhanced-documentation {
  display: flex;
  flex-direction: column;
  gap: var(--spacer, 1rem);
  border-radius: var(--el-border-radius-base);
  color: var(--ks-content-primary);
}

.doc-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacer-sm, 0.75rem);
  background: var(--ks-background-panel);
  border-bottom: 1px solid var(--ks-border-primary);
  padding: 1rem 2rem;
  position: sticky;
  top: 0;
  z-index: 1;
  margin-left: -1rem;
  margin-right: -1rem;
  width: calc(100% + 2rem);
}

.doc-search {
  flex: 1 1 auto;
  :deep(.el-input__wrapper) {
    background: var(--ks-background-input);
    box-shadow: none;
    border: 1px solid var(--ks-border-primary);
    border-radius: var(--el-border-radius-base);
  }
  :deep(.el-input__inner) {
    font-size: var(--font-size-sm);
  }
}

.doc-search__empty {
  font-size: var(--font-size-xs);
  color: var(--ks-content-secondary);
  white-space: nowrap;
}

.enhanced-markdown {
  font-size: v-bind(fontSizeCss);
  display: flex;
  flex-direction: column;
  gap: var(--spacer, 1rem);
}

/* Heading decoration */
:deep(.doc-heading) {
  color: var(--ks-content-primary);
  position: relative;
  padding-left: 1rem;
  margin-bottom: 0.5rem;
}
:deep(.doc-heading::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 0.35rem;
  bottom: 0.35rem;
  width: 3px;
  background: linear-gradient(180deg, var(--bs-code-color) 0%, var(--ks-content-link) 100%);
  border-radius: 999px;
}

/* Section wrapper */
:deep(.doc-section) {
  background: var(--ks-background-panel);
  border: 1px solid var(--ks-border-primary);
  border-radius: var(--el-border-radius-base);
  padding: 1rem 1.25rem;
  box-shadow: none;
}

/* Collapsibles */
:deep(details.doc-collapsible) {
  background: var(--ks-background-card);
  border: 1px solid var(--ks-border-primary);
  border-radius: var(--el-border-radius-base);
  margin-top: 0.75rem;
  overflow-x: scroll;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
:deep(details.doc-collapsible[open]) {
  box-shadow: 0 10px 30px rgba(37, 56, 88, 0.12);
  border-color: var(--ks-border-tertiary, var(--ks-border-primary));
}
:deep(details.doc-collapsible.is-dimmed) {
  opacity: 0.4;
}
:deep(details.doc-collapsible summary) {
  list-style: none;
  padding: 0.85rem 1rem;
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
:deep(details.doc-collapsible summary::-webkit-details-marker) {
  display: none;
}
:deep(details.doc-collapsible summary h3) {
  margin: 0;
  font-size: var(--font-size-lg);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
:deep(details.doc-collapsible summary h3 .header-anchor) {
  opacity: 0;
  transition: opacity 0.2s ease;
}
:deep(details.doc-collapsible summary:hover h3 .header-anchor) {
  opacity: 1;
}
:deep(details.doc-collapsible .doc-collapsible__content) {
  padding: 0 1rem 1rem 1rem;
  display: flex;
  flex-direction: column;
  gap: var(--spacer-sm, 0.75rem);
}

/* Card grid + card */
:deep(.doc-card-grid) {
  container-type: inline-size;
  display: grid;
  gap: var(--spacer, 1rem);
  // single column when component is narrow, otherwise auto-fit with a ma of 2 columns
  // 45% is the min-size of a column when 2 fit side by side with gap
  grid-template-columns: repeat(auto-fit, minmax(max(260px, 45%), 1fr));
  align-items: start;
}

:deep(.doc-card) {
  background: var(--ks-background-panel);
  border: 1px solid var(--ks-border-primary);
  border-radius: var(--el-border-radius-base);
  padding: 1rem;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 0;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  min-width: 0;
}
:deep(.doc-card.is-hidden) {
  display: none;
}
:deep(.doc-card__header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  width: 100%;
  min-width: 0;
  background: none;
  border: none;
  color: var(--ks-content-primary);
  font-weight: 600;
  font-size: var(--font-size-base);
  padding: 0;
  cursor: pointer;
  text-align: left;
  transition: color 0.2s ease;
}
:deep(.doc-card__header:focus-visible) {
  outline: 2px solid var(--bs-code-color);
  outline-offset: 2px;
}
:deep(.doc-card__header--static) {
  cursor: default;
  pointer-events: none;
}
:deep(.doc-card__title) {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
:deep(.doc-card__indicator) {
  width: 0.85rem;
  height: 0.85rem;
  color: var(--ks-content-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
:deep(.doc-card__indicator::before) {
  content: "";
  border-style: solid;
  border-width: 0 2px 2px 0;
  display: inline-block;
  padding: 3px;
  transform: rotate(45deg);
  transition: transform 0.2s ease;
  border-color: currentColor;
}
:deep(.doc-card.is-open .doc-card__indicator::before) {
  transform: rotate(225deg);
}
:deep(.doc-card__body) {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 0.25s ease, opacity 0.2s ease;
}
:deep(.doc-card.is-open .doc-card__body) {
  max-height: 1000px;
  opacity: 1;
  margin-top: 0.75rem;
}

/* Example wrapper */
:deep(.doc-example) {
  margin-top: 1rem;
  border: 1px solid var(--ks-border-primary);
  border-radius: var(--el-border-radius-base);
  background: var(--ks-background-card);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
:deep(.doc-example__toggle) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  background: none;
  border: none;
  color: var(--ks-content-primary);
  font-weight: 600;
  font-size: var(--font-size-base);
  text-align: left;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}
:deep(.doc-example__toggle:hover) {
  background: rgba(124, 58, 237, 0.08);
  color: var(--bs-code-color);
}
:deep(.doc-example__toggle:focus-visible) {
  outline: 2px solid var(--bs-code-color);
  outline-offset: 2px;
}
:deep(.doc-example__label) {
  flex: 1 1 auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
:deep(.doc-example__indicator) {
  width: 0.85rem;
  height: 0.85rem;
  color: var(--ks-content-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;
  position: relative;
}
:deep(.doc-example__indicator::before) {
  content: "";
  border-style: solid;
  border-width: 0 2px 2px 0;
  display: inline-block;
  padding: 3px;
  transform: rotate(45deg);
  border-color: currentColor;
}
:deep(.doc-example.is-open .doc-example__indicator::before) {
  transform: rotate(135deg);
}
:deep(.doc-example__content) {
  border-top: 1px solid var(--ks-border-primary);
  max-height: 0;
  opacity: 0;
  padding: 0 1rem;
  overflow: hidden;
  transition: max-height 0.25s ease, opacity 0.2s ease;
}
:deep(.doc-example.is-open .doc-example__content) {
  opacity: 1;
  padding: 1rem;
}
:deep(.doc-example__content .doc-code-block) {
  margin-top: 0.75rem;
}

/* Card item */
:deep(.doc-card__item) {
  background: var(--ks-background-panel);
  border: 1px solid var(--ks-border-tertiary, rgba(148, 163, 184, 0.2));
  border-radius: var(--el-border-radius-sm);
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
:deep(.doc-card__label) {
  font-size: var(--font-size-xs);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ks-content-tertiary);
}
:deep(.doc-card__value) {
  font-size: var(--font-size-sm);
  color: var(--ks-content-primary);
  line-height: 1.5;
}

/* Code block / toolbar / copy */
:deep(.doc-code-block) {
  background: var(--ks-background-panel);
  border: 1px solid var(--ks-border-primary);
  border-radius: var(--el-border-radius-lg);
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
}
:deep(.doc-code-toolbar) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.9rem;
  background: var(--ks-background-card-secondary, rgba(76, 29, 149, 0.06));
  border-bottom: 1px solid var(--ks-border-primary);
}
:deep(.doc-code-language) {
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ks-content-secondary);
}
:deep(.doc-copy-button) {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid transparent;
  background: var(--ks-button-background-secondary, transparent);
  color: var(--ks-content-secondary);
  border-radius: var(--el-border-radius-sm);
  padding: 0.25rem 0.5rem;
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}
:deep(.doc-copy-button:hover) {
  background: rgba(124, 58, 237, 0.12);
  color: var(--bs-code-color);
  border-color: rgba(124, 58, 237, 0.35);
}
:deep(.doc-copy-button.copied) {
  background: rgba(34, 197, 94, 0.14);
  color: var(--ks-content-success);
  border-color: rgba(34, 197, 94, 0.45);
}
:deep(.doc-code-block pre) {
  margin: 0;
  border-radius: 0 0 var(--el-border-radius-lg) var(--el-border-radius-lg);
}
:deep(.doc-code-block code) {
  font-family: "Source Code Pro", monospace;
  font-size: 0.85rem;
}
:deep(.doc-collapsible__content p) {
  margin: 0;
  line-height: 1.7;
}

/* Responsive */
@media (max-width: 768px) {
  .enhanced-documentation {
    gap: 1rem;
  }
  .doc-toolbar {
    position: static;
  }
}

@media (max-width: 640px) {
  :deep(.doc-card-grid) {
    grid-template-columns: 1fr;
  }
  :deep(.doc-card) {
    padding: 0.85rem;
  }
}

/* simple fade transition for search helper */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
