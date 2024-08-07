import type {Plugin} from "vite";
import {execSync} from "child_process";

const getInfo = (formats: string[]): string[] => formats.map(format => execSync(`git log -1 --format=${format}`).toString().trim());

const comment = (message: string, author: string, date: string): string => `
<!--

    Last Commit: 

    ${message}
    ----------
    Author: ${author}
    Date: ${date}

-->`;

export const commit = (): Plugin => {
    const [message, author, date] = getInfo(["%s", "%an", "%cd"]);

    return {
        name: "commit",
        transformIndexHtml: {
            order: "pre",
            handler(html: string): string {
                return comment(message, author, date) + html;
            },
        },
    };
};
