import fs from "node:fs"
import path from "node:path"
import {describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n, useI18n} from "vue-i18n"
import bundled from "./translations/en.json"
import {registerTopologyMessages} from "./i18n"

type Messages = {[key: string]: string | Messages}

const flatten = (messages: Messages, prefix = ""): Record<string, string> =>
    Object.fromEntries(Object.entries(messages).flatMap(([key, value]) =>
        typeof value === "string" ? [[prefix + key, value]] : Object.entries(flatten(value, `${prefix}${key}.`))))

const lookup = (messages: Messages, key: string): unknown =>
    key in messages ? messages[key] : key.split(".").reduce<unknown>((node, part) => (node as Messages | undefined)?.[part], messages)

const sourceKeys = () => {
    const keys = new Set<string>()
    const walk = (dir: string) => {
        for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
            const file = path.join(dir, entry.name)
            if (entry.isDirectory()) walk(file)
            else if (/\.(vue|ts)$/.test(entry.name) && !entry.name.endsWith(".test.ts")) {
                const source = fs.readFileSync(file, "utf8")
                for (const match of source.matchAll(/(?:\$t|(?<![.\w])t)\(\s*["'`]([^"'`$]+)["'`]/g)) keys.add(match[1])
                for (const match of source.matchAll(/label:\s*"([^"]+)",\s*\/\/ i18n key/g)) keys.add(match[1])
            }
        }
    }
    walk(__dirname)
    return [...keys]
}

describe("bundled English messages", () => {
    const ui = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../../../src/translations/en.json"), "utf8")).en as Messages

    it("cover every key the graph translates", () => {
        const missing = sourceKeys().filter((key) => typeof lookup(bundled as Messages, key) !== "string")

        expect(missing).toEqual([])
    })

    it("match the Kestra UI's English", () => {
        // On failure, copy the new values from ui/src/translations/en.json into src/translations/en.json.
        for (const [key, value] of Object.entries(flatten(bundled as Messages))) {
            expect(lookup(ui, key), key).toBe(value)
        }
    })
})

describe("registerTopologyMessages", () => {
    const translate = (messages: Messages, key: string) => {
        let text = ""
        const Probe = defineComponent({
            setup() {
                registerTopologyMessages()
                const {t} = useI18n()
                text = t(key)
                return () => h("div")
            },
        })
        mount(Probe, {global: {plugins: [createI18n({legacy: false, locale: "en", messages: {en: messages}})]}})
        return text
    }

    it("fills in the labels an app does not translate", () => {
        expect(translate({}, "topology-graph.zoom-in")).toBe("Zoom in")
    })

    it("keeps the app's own translation", () => {
        expect(translate({"topology-graph": {"zoom-in": "Magnify"}}, "topology-graph.zoom-in")).toBe("Magnify")
    })
})
