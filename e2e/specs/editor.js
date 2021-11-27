const { evaluate } = require("taiko");

module.exports.setEditorText = async (text) => {
    await evaluate(
        (_, args) => {
            document
                .querySelector(".editor-wrapper")
                .firstChild.__vue__.getEditor()
                .getModel()
                .setValue(args[0]);
        },
        { args: [text] }
    );
};
