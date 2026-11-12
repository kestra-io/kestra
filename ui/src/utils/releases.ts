import semver from "semver"

export interface Release {
    version: string;
    publicationDate: string;
    summary?: string;
    link?: string;
}

export interface ReleaseResponse {
    releases?: Release[];
}

interface DatedEntry {
    publicationDate: string;
}

// Minor and major releases are announced through the news feed, so only newer patches of the running minor are kept.
export function patchReleasesFor(releases: Release[], currentVersion: string): Release[] {
    const current = semver.coerce(currentVersion)
    if (current === null) return []

    return releases
        .map(release => ({release, parsed: semver.coerce(release.version)}))
        .filter((entry): entry is {release: Release; parsed: semver.SemVer} =>
            entry.parsed !== null
            && entry.parsed.major === current.major
            && entry.parsed.minor === current.minor
            && entry.parsed.patch > current.patch,
        )
        .sort((a, b) => semver.rcompare(a.parsed, b.parsed))
        .map(entry => entry.release)
}

export function releaseNotesUrl(release: Release): string {
    return release.link ?? `https://github.com/kestra-io/kestra/releases/tag/v${release.version}`
}

export function latestNewsDate(feeds: DatedEntry[] = [], releases: DatedEntry[] = []): string | null {
    let latest: string | null = null

    for (const {publicationDate} of [...feeds, ...releases]) {
        if (latest === null || new Date(publicationDate) > new Date(latest)) {
            latest = publicationDate
        }
    }

    return latest
}
