import { describe, test } from "vitest";
import { composeStories } from "@storybook/vue3";
import * as detailsStories from "../storybook/HttpTopologyDetails.stories";
import * as drawerStories from "../storybook/HttpTaskDrawer.stories";

// Story.run() renders the story and executes its `play` assertions in jsdom, no browser needed.
for (const [suite, stories] of [
    ["HttpTopologyDetails", composeStories(detailsStories)],
    ["HttpTaskDrawer", composeStories(drawerStories)],
] as const) {
    describe(`${suite} stories`, () => {
        for (const [storyName, Story] of Object.entries(stories)) {
            if (typeof Story.play !== "function") continue;
            test(storyName, async () => {
                await Story.run();
            });
        }
    });
}