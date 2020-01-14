# VSCode

## Setup

1. Ensure some appropriate `deps.edn` has been [edited appropriately](../README.md#general-setup-and-use).

2. Index a Clojure code base:

   ```
   $ cd /home/alice/a-clj-proj-dir
   $ clj -A:ctags
   ```

3. Install an appropriate extension.  I had luck with [ctagsx](https://github.com/jtanx/ctagsx).

4. It might be that editting `settings.json` to also contain:

   ```
    "[clojure]": {
      "editor.wordSeparators": "\t ()\"':,;~@#$%^&{}[]`"
    }
   ```

   will yield better behavior when selecting an identifier by double-clicking.  If your setup uses [Calva](https://github.com/BetterThanTomorrow/calva), it might not be necessary.

## Try it out

1. Launch VSCode and open a folder for a Clojure project

2. Put the cursor on an identifier you want to look up and double-click to select it

3. Do one of the following:

   * Press `F12`
   * Choose `Go -> Go to Definition` via the menu
   * From the context-sensitive menu, choose `Go to Definition`
   * Enter `Go to Definition` into your friendly command palette

(The `Go Back` command is helpful for going back to where you were before.  You can look up what the keybinding is via the command palette, but it may be one of: `Ctrl Minus`, `Ctrl Alt Minus`, `Alt Left`)
