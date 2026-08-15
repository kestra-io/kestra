import path from "path"
import {fileURLToPath} from "url"
import {compareTranslations, DEFAULT_LANGUAGES} from "./compareTranslations"

const here = path.dirname(fileURLToPath(import.meta.url))

// The tooling lives here; the language files stay in `src/` because the app imports them
// (`src/translations/i18n.ts` globs them relative to itself). The design system ships the same
// layout of its own, so it can lazy-load a language without the app — one directory, one
// fingerprints file, the same check.
const SETS = [
    {
        label: "app",
        translationsDir: path.resolve(here, "../../src/translations"),
        fingerprintsFile: path.join(here, "fingerprints.json"),
    },
    {
        label: "design system",
        translationsDir: path.resolve(here, "../../packages/design-system/src/translations"),
        fingerprintsFile: path.join(here, "fingerprints-design-system.json"),
    },
]

// Both sets are always checked, so one broken directory doesn't hide the state of the other.
const failures = SETS.flatMap(({label, translationsDir, fingerprintsFile}) => {
    try {
        compareTranslations(translationsDir, DEFAULT_LANGUAGES, fingerprintsFile, label)
        return []
    } catch (error) {
        return [(error as Error).message]
    }
})

if (failures.length) {
    throw new Error(failures.join("\n"))
}
