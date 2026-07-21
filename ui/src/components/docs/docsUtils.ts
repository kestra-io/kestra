export const SECTIONS = {
    "Get Started with Kestra": [
        "Quickstart",
        "Installation Guide",
        "Tutorial",
        "Architecture",
        "User Interface",
    ],
    "Build with Kestra": [
        "Concepts",
        "Workflow Components",
        "Multi-Language Script Tasks",
        "AI Tools",
        "No-Code",
        "Version Control & CI/CD",
        "Plugin Developer Guide",
        "How-to Guides",
    ],
    "Scale with Kestra": [
        "Cloud & Enterprise Edition",
        "Task Runners",
        "Best Practices",
    ],
    "Manage Kestra": [
        "Administrator Guide",
        "Migration Guide",
        "Performance",
    ],
    "Reference Docs": [
        "Configuration",
        "Releases & LTS Policy",
        "Expressions",
        "API Reference",
        "Terraform Provider",
    ],
}

export const DISABLED_PAGES = [
    "docs/api-reference",
    "docs/terraform/data-sources",
    "docs/terraform/guides",
    "docs/terraform/resources",
]

export function removeMDXImports(content: string): string {
    // we want to only remove lines that are not in a code block
    // so we isolate code blocks first
    const contentArray = content.split("```")
    for(let i = 0; i < contentArray.length; i++){
        // if the index is even, it's outside a code block
        if(i % 2 === 0){
            // remove lines that start with `import`
            // to keep compatibility with mdx files
            // without splitting and rejoining since it would
            // create huge arrays just to destroy them right after
            contentArray[i] = contentArray[i].replaceAll(/import [\s\S]+? from ['"][\s\S]+?['"];?/g, "")
        }
    }
    return contentArray.join("```")
}

export function extractMultilineJSXComponents(content: string) {
    // first, find every line that start with < and a capital letter, and that doesn't end with />
    const lines = content.split("\n")
    const linesToRemove: number[] = []
    const removedComponents: Record<number, string> = {}
    let startOfBlockLine = -1
    let componentName = ""
    let insideCodeBlock = false
    let currentBlockLines: number[] = []

    for(let i = 0; i < lines.length; i++){
        if(insideCodeBlock){
            if(lines[i].match(/^```/)){
                insideCodeBlock = false
            }
            continue
        } else {
            if(lines[i].match(/^```/)){
                insideCodeBlock = true
                continue
            }
        }

        if(startOfBlockLine > -1){
            // if an empty line appears, MDX will consider it a stop in the JSX
            if(lines[i].trim() === ""){
                startOfBlockLine = -1
                componentName = ""
                currentBlockLines = []
                continue
            }

            currentBlockLines.push(i)

            // if we have started a block, let's check if this line is the end of it.
            // if so, we remove it and stop the next iterations until we find a new block
            if(lines[i].match(/^\/>/)){
                removedComponents[startOfBlockLine] = lines.slice(startOfBlockLine, i).join("\n") + `\n></${componentName}>`
                startOfBlockLine = -1
                componentName = ""
                // and only once we are sure the block is closed,
                // do we add the lines to remove
                linesToRemove.push(...currentBlockLines)
                currentBlockLines = []
            }
        }

        if(lines[i].match(/^<([A-Z][\w]*)\b(?![^>]*\/>).*$/)){
            componentName = lines[i].match(/^<([A-Z][\w]*)/)?.[1] ?? ""
            startOfBlockLine = i
        }
    }

    // in place of each removed block, we add a placeholder with the component name to keep track of where it was in the doc
    for(const lineIndex in removedComponents){
        lines[lineIndex] = `<!-- ${removedComponents[lineIndex]} -->`
    }
    return {
        content: lines.filter((_, i) => !linesToRemove.includes(i)).join("\n"),
        removedComponents: removedComponents,
    }
}

export function replaceSelfClosingTagsWithOpenClose(content: string): string {
    // we want to replace every self closing tag with an open and close tag
    // to keep compatibility with mdx files that use self closing tags for custom components
    return content.replaceAll(/<([A-Z][\w]*)\b([^>]*)\/>/g, "<$1$2></$1>\n")
}
