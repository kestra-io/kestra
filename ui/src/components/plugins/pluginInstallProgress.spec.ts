import {describe, expect, it} from "vitest"
import {artifactPercentage, humanBytes, progressFor} from "./pluginInstallProgress"
import type {ArtifactProgress, PluginArtifact} from "../../stores/plugins"

const artifact = (artifactId?: string): PluginArtifact => ({artifactId})

const progress = (transferred?: number, total?: number): ArtifactProgress => ({transferred, total})

const downloadPath = (fileName: string): string => `https://repo.example.com/plugins/${fileName}`

describe("progressFor", () => {
    it("matches the artifact's own entry by its file segment", () => {
        const entry = progress(512, 1024)

        expect(progressFor({[downloadPath("plugin-aws-1.2.3.jar")]: entry}, artifact("plugin-aws"))).toBe(entry)
    })

    it("does not match a longer artifact id that shares the prefix", () => {
        const s3 = progress(1, 10)

        expect(progressFor({[downloadPath("plugin-aws-s3-1.0.0.jar")]: s3}, artifact("plugin-aws"))).toBeUndefined()
    })

    it("matches the longer artifact id when it is the one requested", () => {
        const s3 = progress(1, 10)

        expect(progressFor({[downloadPath("plugin-aws-s3-1.0.0.jar")]: s3}, artifact("plugin-aws-s3"))).toBe(s3)
    })

    it("returns undefined when no entry matches", () => {
        expect(progressFor({[downloadPath("plugin-gcp-1.0.0.jar")]: progress(1, 10)}, artifact("plugin-aws"))).toBeUndefined()
    })

    it("escapes a dot in the artifact id", () => {
        const entry = progress(1, 10)

        // An unescaped dot would also match "pluginXaws-1.0.0.jar".
        expect(progressFor({[downloadPath("plugin.aws-1.0.0.jar")]: entry}, artifact("plugin.aws"))).toBe(entry)
        expect(progressFor({[downloadPath("pluginXaws-1.0.0.jar")]: entry}, artifact("plugin.aws"))).toBeUndefined()
    })

    it("escapes a plus sign in the artifact id", () => {
        const entry = progress(1, 10)

        // A '+' is a quantifier when it is not escaped, so the id must still match literally.
        expect(progressFor({[downloadPath("plugin+aws-1.0.0.jar")]: entry}, artifact("plugin+aws"))).toBe(entry)
        expect(progressFor({[downloadPath("pluginaws-1.0.0.jar")]: entry}, artifact("plugin+aws"))).toBeUndefined()
    })

    it("escapes a character class in the artifact id", () => {
        const entry = progress(1, 10)

        // Unescaped, '[bar]+' would also match 'foobarbaz'.
        expect(progressFor({[downloadPath("plugin-foo[bar]+baz-1.0.0.jar")]: entry}, artifact("plugin-foo[bar]+baz"))).toBe(entry)
        expect(progressFor({[downloadPath("plugin-foobarbaz-1.0.0.jar")]: entry}, artifact("plugin-foo[bar]+baz"))).toBeUndefined()
    })
})

describe("artifactPercentage", () => {
    it("rounds transferred over total", () => {
        expect(artifactPercentage({[downloadPath("plugin-aws-1.2.3.jar")]: progress(1, 3)}, artifact("plugin-aws"))).toBe(33)
        expect(artifactPercentage({[downloadPath("plugin-aws-1.2.3.jar")]: progress(2, 3)}, artifact("plugin-aws"))).toBe(67)
        expect(artifactPercentage({[downloadPath("plugin-aws-1.2.3.jar")]: progress(50, 100)}, artifact("plugin-aws"))).toBe(50)
    })

    it("is 0 when there is no matching entry", () => {
        expect(artifactPercentage({}, artifact("plugin-aws"))).toBe(0)
    })

    it("is 0 when the entry has no total", () => {
        expect(artifactPercentage({[downloadPath("plugin-aws-1.2.3.jar")]: progress(10, undefined)}, artifact("plugin-aws"))).toBe(0)
    })

    it("is 0 when the total is 0", () => {
        expect(artifactPercentage({[downloadPath("plugin-aws-1.2.3.jar")]: progress(10, 0)}, artifact("plugin-aws"))).toBe(0)
    })

    it("is 0 when transferred is missing", () => {
        expect(artifactPercentage({[downloadPath("plugin-aws-1.2.3.jar")]: {total: 100}}, artifact("plugin-aws"))).toBe(0)
    })
})

describe("humanBytes", () => {
    it.each([
        [0, "0 B"],
        [1023, "1023 B"],
        [1024, "1.0 KB"],
        [1536, "1.5 KB"],
        [1024 * 1024 - 1024, "1023.0 KB"],
        [1024 * 1024, "1.0 MB"],
        [1024 * 1024 + 512 * 1024, "1.5 MB"],
    ] as const)("renders %s bytes as %s", (bytes, expected) => {
        expect(humanBytes(bytes)).toBe(expected)
    })
})
