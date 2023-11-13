import decompress from "decompress";

export default (options = {}) => {
    const { targets = [] } = options
    return {
        name: 'unzip',
        ['buildStart']: async() => {
            const extractions = targets.map(async target => {
                await decompress(target.archive, target.outputDir);
            });

            return await Promise.all(extractions);
        }
    }
}