import decompress from "decompress";
import fs from "fs";
import https from "https";

async function fetchAndExtract(target) {
    return new Promise((resolve) => {
        const folderName = target.outputDir.split("/").pop();

        const file = fs.createWriteStream(`public/vscode/extensions/${folderName}.vsix`);
        https.get(target.vsixUrl, async response => {
            // we follow redirect
            if (response.statusCode === 302) {
                await fetchAndExtract({...target, vsixUrl: response.headers.location});
                resolve();
                return;
            }

            // after download completed close filestream
            file.on("finish", async () => {
                file.close();
                await decompress(file.path, target.outputDir);
                await fs.unlinkSync(`public/vscode/extensions/${folderName}.vsix`);
                resolve();
            });

            response.pipe(file);
        });
    });
}

export default (options = {}) => {
    const {targets = []} = options
    return {
        name: "download-vsix",
        ["buildStart"]: () => {
            const extractions = targets.map(target => {
                // extension is already downloaded and extracted
                if (fs.existsSync(target.outputDir)) {
                    return;
                }

                const parentDir = target.outputDir.substring(0, target.outputDir.lastIndexOf("/"));
                if (!fs.existsSync(parentDir)) {
                    fs.mkdirSync(parentDir, {recursive: true});
                }

                return fetchAndExtract(target);
            });

            return Promise.all(extractions);
        }
    }
}