Title: UI: Set diff editor inserted line success background color (light + dark)

Summary:
- Implements ticket: "Code colors: Change success background color".
- Applies success background color to Monaco diff editor inserted lines for both themes.

Changes:
- File: ui/src/components/inputs/MonacoEditor.vue
  - Dark theme:
    "diffEditor.insertedLineBackground": "#029E734D"
  - Light theme:
    "diffEditor.insertedLineBackground": "#BEEFE2"

Rationale:
- Monaco’s diff editor uses the "diffEditor.insertedLineBackground" theme key to color the background of inserted lines.
- Prior to this change, the key was not set in our themes, resulting in default colors.
- The new values align with the app’s success tones; light mode uses #BEEFE2 (matches success background tone used across the UI), and dark mode uses a semi-transparent success green #029E734D to ensure adequate contrast on dark backgrounds.
- The ticket mentions the CSS variable ks-background-additionLine; Monaco themes accept hex values, so the equivalent hex values are used directly.

Verification steps:
1) Start the UI in develop and open any code diff editor (Flow changes, etc.).
2) Verify inserted lines in the diff view show the success background color:
   - Light mode: pastel green (#BEEFE2).
   - Dark mode: transparent green overlay (#029E734D).
3) Toggle theme (light/dark) and confirm colors change accordingly.
4) Ensure regular editor colors (selection, highlight) remain unaffected.

Notes:
- No logic changes; only theme color additions.
- If desired, we can later map Monaco colors dynamically from CSS variables, but Monaco requires computed hex values at theme definition time.

References:
- Ticket: "Code colors: Change success background color"
- Screenshot: https://github.com/user-attachments/assets/ce4208d4-c151-40d4-bd72-0a9c3294e6c0

Suggested commit message:
ui: set Monaco diffEditor inserted line success background for light and dark themes

Suggested PR reviewers:
- UI maintainers familiar with Monaco integration and theme variables.