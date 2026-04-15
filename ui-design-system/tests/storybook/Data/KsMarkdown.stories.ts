import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsMarkdown from "../../../src/components/Data/KsMarkdown/KsMarkdown.vue"

const meta: Meta<typeof KsMarkdown> = {
    title: "Components/Data/KsMarkdown",
    component: KsMarkdown,
    tags: ["autodocs"],
    argTypes: {
        content: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component: [
                    "KsMarkdown renders a Markdown string into a rich HTML view using the `unified` ecosystem.",
                    "",
                    "**Features:**",
                    "- GitHub Flavored Markdown (GFM): tables, strikethrough, autolinks",
                    "- Fenced code blocks with a **Copy** button and language label",
                    "- ` ```mermaid ` blocks rendered as Mermaid diagrams",
                    "- Headings with hoverable `#` anchor links",
                    "- `:::alert{type}` directive → `KsAlert` component",
                ].join("\n"),
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsMarkdown>

// ─── Shared sample content ────────────────────────────────────────────────────

const HTML_EXAMPLE = `
<p>
  Please press <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>R</kbd> to refresh <mark>this page</mark>.<br />
  <var>l</var> × <var>w</var> × <var>h</var>
</p>

<div class="video-container">
  <iframe src="https://www.youtube.com/embed/BeQNI2XRddA?si=nvoIqA1SIrMaKyYs" title="YouTube video player" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
</div>
`
const KITCHEN_SINK = `
---
title: Kestra Markdown Component
---
# Kestra Markdown Component

A complete overview of all supported features.

## Text Formatting

Normal paragraph with **bold text**, _italic text_, ~~strikethrough~~ and \`inline code\`.

> This is a blockquote. It can contain **formatted** text and multiple sentences spanning
> across lines.

---

## Headings

### H3 Heading
#### H4 Heading
##### H5 Heading
###### H6 Heading

## Code Blocks

\`\`\`yaml
id: my-flow
namespace: company.team
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello, World!
\`\`\`

\`\`\`typescript
async function fetchExecutions(namespace: string): Promise<Execution[]> {
    const response = await fetch(\`/api/v1/executions?namespace=\${namespace}\`)
    return response.json()
}
\`\`\`

## Tables

| Plugin | Type | Description |
|--------|------|-------------|
| Log | Task | Writes a message to the task logs |
| Script | Task | Executes a script |
| Http | Trigger | Listens for incoming HTTP requests |
| Schedule | Trigger | Runs on a cron schedule |

## Lists

- Unordered item one
- Unordered item two
  - Nested item
  - Another nested item
- Unordered item three

1. First ordered step
2. Second ordered step
3. Third ordered step

## Links

[Kestra Documentation](https://kestra.io/docs) — external link opens in a new tab.

[Relative link](/flows) — internal link stays in the same tab.

## Alert Directives

:::alert{type="info"}
This is an **informational** alert. Useful for tips and general notes.
:::

:::alert{type="warning"}
This is a **warning** alert. Use it to highlight potential issues.
:::

:::alert{type="error"}
This is an **error** alert. Indicates something went wrong.
:::

:::alert{type="success"}
This is a **success** alert. Confirms that an action completed successfully.
:::

## Mermaid Diagram

\`\`\`mermaid
graph LR
    Trigger --> Extract
    Extract --> Transform
    Transform --> Load
    Load --> Notify
\`\`\`

## Html raw code
${HTML_EXAMPLE}
`.trim()

const CODE_BLOCKS = `
## Code Blocks with Copy Button

Each block shows the language label and a **Copy** button in the header.

\`\`\`bash
curl -X POST https://kestra.io/api/v1/executions/trigger/company.team/my-flow \\
  -H "Authorization: Bearer $TOKEN"
\`\`\`

\`\`\`python
import requests

response = requests.post(
    "https://kestra.io/api/v1/executions",
    json={"namespace": "company.team", "flowId": "my-flow"},
    headers={"Authorization": f"Bearer {token}"},
)
print(response.json())
\`\`\`

\`\`\`sql
SELECT
    namespace,
    flow_id,
    COUNT(*) AS total,
    SUM(CASE WHEN state = 'SUCCESS' THEN 1 ELSE 0 END) AS succeeded
FROM executions
WHERE created_at >= NOW() - INTERVAL '7 days'
GROUP BY 1, 2
ORDER BY total DESC;
\`\`\`

Block without a language label:

\`\`\`
plain text code block
no syntax highlighting
\`\`\`
`.trim()

const ALERT_DIRECTIVES = `
## Alert Directives

Use the \`:::alert{type}\` directive to embed a \`KsAlert\` component inline.

:::alert{type="info"}
**Info** — General information or tips. Markdown inside the alert is fully rendered: \`inline code\`, **bold**, _italic_.
:::

:::alert{type="success"}
**Success** — Indicates a positive outcome or completed action.
:::

:::alert{type="warning"}
**Warning** — Highlights something that needs attention before proceeding.
:::

:::alert{type="error"}
**Error** — Signals a problem that must be resolved.
:::
`.trim()

const GFM_FEATURES = `
## GitHub Flavored Markdown

### Tables with alignment

| Left aligned | Centered | Right aligned |
|:-------------|:--------:|--------------:|
| Alpha        |  Beta    |         Gamma |
| Delta        |  Epsilon |         Zeta  |
| 1            |    2     |             3 |

### Strikethrough

~~This text has been removed~~ — GFM strikethrough via double tildes.

### Autolinks

Visit https://kestra.io or email support@kestra.io — bare URLs become clickable links.
`.trim()

const MERMAID_EXAMPLE = `
## Mermaid Diagrams

Fenced code blocks with language \`mermaid\` are rendered as diagrams.

### Flow

\`\`\`mermaid
flowchart TD
    A([Trigger]) --> B[Extract data]
    B --> C{Valid?}
    C -- Yes --> D[Transform]
    C -- No --> E[Send alert]
    D --> F([Load to warehouse])
\`\`\`

### Sequence

\`\`\`mermaid
sequenceDiagram
    participant Client
    participant API
    participant Worker
    Client->>API: POST /executions
    API->>Worker: dispatch job
    Worker-->>API: job completed
    API-->>Client: 200 OK
\`\`\`
`.trim()

// ─── Stories ──────────────────────────────────────────────────────────────────

/** Controllable via the Storybook controls panel — edit the `content` text area to preview any markdown. */
export const Default: Story = {
    render: (args) => ({
        components: {KsMarkdown},
        setup() { return {args} },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown v-bind=\"args\" /></div>",
    }),
    args: {
        content: "Hello **Kestra**! This is `KsMarkdown` in action.\n\n> Try editing the `content` control above.",
    },
}

/** All supported features in one story. */
export const KitchenSink: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() { return {content: KITCHEN_SINK} },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown :content=\"content\" /></div>",
    }),
}

/** Fenced code blocks with language labels and the copy-to-clipboard button. */
export const CodeBlocks: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() { return {content: CODE_BLOCKS} },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown :content=\"content\" /></div>",
    }),
}

/** The `:::alert{type}` directive maps to `KsAlert` – all four variants. */
export const AlertDirectives: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() { return {content: ALERT_DIRECTIVES} },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown :content=\"content\" /></div>",
    }),
}

/** GFM extensions: aligned tables, strikethrough, autolinks. */
export const GFMFeatures: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() { return {content: GFM_FEATURES} },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown :content=\"content\" /></div>",
    }),
}

/** Mermaid diagrams rendered from fenced ` ```mermaid ` blocks. */
export const MermaidDiagrams: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() { return {content: MERMAID_EXAMPLE} },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown :content=\"content\" /></div>",
    }),
}

/** Html blocks. */
export const Html: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() { return {content: HTML_EXAMPLE} },
        template: `<div style="padding:24px;max-width:800px">
            <h1>Html enabled</h1>
            <div style="padding:24px;max-width:800px"><ks-markdown :content="content" /></div>

            <h1>Html disabled</h1>
            <div style="padding:24px;max-width:800px"><ks-markdown :content="content" :html="false" /></div>
        </div>`,
    }),
}


/** Headings H1–H6 with hoverable `#` anchor links. */
export const HeadingLinks: Story = {
    render: () => ({
        components: {KsMarkdown},
        setup() {
            return {
                content: [
                    "# H1 — Main title",
                    "## H2 — Section",
                    "### H3 — Subsection",
                    "#### H4 — Detail",
                    "##### H5 — Sub-detail",
                    "###### H6 — Deepest level",
                    "",
                    "Hover any heading to reveal the `#` anchor link.",
                ].join("\n"),
            }
        },
        template: "<div style=\"padding:24px;max-width:800px\"><ks-markdown :content=\"content\" /></div>",
    }),
}
