import {describe, it, expect} from "vitest"
import {latestNewsDate, patchReleasesFor, releaseNotesUrl} from "./releases"

const release = (version: string, publicationDate = "2026-11-01T00:00:00Z") => ({version, publicationDate})

describe("patchReleasesFor", () => {
    it("keeps only newer patches of the running minor, newest first", () => {
        const result = patchReleasesFor(
            [release("2.0.9"), release("2.0.11"), release("2.0.10"), release("2.1.0"), release("1.3.47"), release("3.0.1")],
            "2.0.9",
        )

        expect(result.map(r => r.version)).toEqual(["2.0.11", "2.0.10"])
    })

    it("treats a snapshot of the running version as that version", () => {
        expect(patchReleasesFor([release("2.0.10")], "2.0.9-SNAPSHOT").map(r => r.version)).toEqual(["2.0.10"])
    })

    it("returns nothing for an unparsable current version or release", () => {
        expect(patchReleasesFor([release("2.0.10")], "unknown")).toEqual([])
        expect(patchReleasesFor([release("latest")], "2.0.9")).toEqual([])
    })
})

describe("releaseNotesUrl", () => {
    it("prefers the link from the API and falls back to the GitHub release", () => {
        expect(releaseNotesUrl({...release("2.0.10"), link: "https://kestra.io/docs/changelog"})).toBe("https://kestra.io/docs/changelog")
        expect(releaseNotesUrl(release("2.0.10"))).toBe("https://github.com/kestra-io/kestra/releases/tag/v2.0.10")
    })
})

describe("latestNewsDate", () => {
    it("picks the most recent date across feeds and releases", () => {
        expect(latestNewsDate(
            [{publicationDate: "2026-10-01T00:00:00Z"}],
            [{publicationDate: "2026-11-05T00:00:00Z"}],
        )).toBe("2026-11-05T00:00:00Z")
    })

    it("is null when there is nothing", () => {
        expect(latestNewsDate([], [])).toBeNull()
    })
})
