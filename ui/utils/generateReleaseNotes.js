/**
 * This script fetches GitHub tags from the OSS repository (kestra-io/kestra)
 * and runs changelogen to compare the two latest versions.
 */

import {exec} from "child_process";
import * as process from "node:process";

const MINOR_VERSION = parseInt(process.argv[2], 10) || 21;
const URL = "https://api.github.com/repos/kestra-io/kestra/tags?per_page=100";

async function generateReleaseNotes() {
    try {
        const response = await fetch(URL, {
            headers: {"User-Agent": "Node.js"},
        });
        const tags = await response.json();

        const filteredTags = tags
            .map((tag) => ({
                name: tag.name,
                commit: tag.commit.sha,
                patch: parseInt(
                    tag.name.replace(`v0.${MINOR_VERSION}.`, ""),
                    10,
                ),
            }))
            .filter(
                (tag) =>
                    tag.name.startsWith(`v0.${MINOR_VERSION}.`) &&
                    !tag.name.includes("SNAPSHOT") &&
                    !isNaN(tag.patch),
            )
            .sort((a, b) => b.patch - a.patch)
            .slice(0, 2);

        if (filteredTags.length < 2)
            throw new Error("Not enough versions found.");

        const [lower, higher] = filteredTags.sort((a, b) => a.patch - b.patch);

        console.log(
            `Generating release notes for changes between tags ${lower.name} and ${higher.name}.`,
        );
        exec(
            `npx changelogen@latest --from ${lower.commit} --to ${higher.commit} --output`,
            (error) => {
                if (error) console.error(error);
            },
        );
    } catch (error) {
        console.error("Error:", error);
    }
}

generateReleaseNotes();
