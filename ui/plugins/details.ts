import type {Plugin} from "vite";
import {execSync} from "child_process";

const comment = (hash: string, date: string): string => {
    return `
<!--

    Last Commit: 
      
    URL: https://github.com/kestra-io/kestra/commit/${hash}
    Date: ${date}

-->`;
};

export const details = (): Plugin => {
    const hash: string = execSync("git rev-parse --short HEAD").toString().trim();
    const date: string = execSync("git log -1 --format=%cd").toString().trim();

    return {
        name: "details",
        transformIndexHtml: {
            order: "pre",
            handler(html: string): string {
                return comment(hash, date) + html;
            },
        },
    };
};
