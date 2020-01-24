# alc.index-defs

## Purpose

Create index files for source code...including dependencies.

Currently supported output formats are [tags](http://ctags.sourceforge.net/FORMAT) (vi, et. al) and [TAGS](https://en.wikipedia.org/wiki/Ctags#Etags_2) (Emacs).

This is a means to an end -- the primary goal is to be able to "jump to definition".  In some cases one might derive additional benefits, for example, improved completion or renaming of identifiers.

## Prerequisites

* clj / clojure

## Use Cases and Limitations

[When](doc/use-cases.md) this might be useful, and the [fine print](doc/limitations.md).

## Quick Trial

Suppose there is a Clojure project directory `/home/alice/a-clj-proj-dir`.

Create a `TAGS` file:

```
$ cd /home/alice/a-clj-proj-dir
$ clj -Sdeps '{:deps {alc.index-defs {:git/url "https://github.com/sogaiu/alc.index-defs" :sha "d4e4536ca9ef2057436cc299cfe8c0e3946fc00e"}}}' -m alc.index-defs.etags
```

Create a `tags` file:

```
$ cd /home/alice/a-clj-proj-dir
$ clj -Sdeps '{:deps {alc.index-defs {:git/url "https://github.com/sogaiu/alc.index-defs" :sha "d4e4536ca9ef2057436cc299cfe8c0e3946fc00e"}}}' -m alc.index-defs.ctags
```

What is necessary to make use of the result depends on the editor.

I've had varying degrees of success with Emacs, vim, Atom, and VSCode.  In some cases, installation and setup of an appropriate plugin, extension, etc. was necessary.

See below for some details.

## General Setup and Use

Edit `~/.clojure/deps.edn` appropriately.

For `TAGS` (aka etags -- Emacs):

```
  ...
  :aliases
  {
   :etags ; or some other name, :alc.index-defs.etags
   {
    :extra-deps
    {sogaiu/alc.index-defs
      {:git/url "https://github.com/sogaiu/alc.index-defs"
       :sha "d4e4536ca9ef2057436cc299cfe8c0e3946fc00e"}}
    :main-opts ["-m" "alc.index-defs.etags"]
   }
```

For `tags` (aka ctags -- vim, VSCode, Atom, etc.):

```
  ...
  :aliases
  {
   :ctags ; or some other name, :alc.index-defs.ctags
   {
    :extra-deps
    {sogaiu/alc.index-defs
      {:git/url "https://github.com/sogaiu/alc.index-defs"
       :sha "d4e4536ca9ef2057436cc299cfe8c0e3946fc00e"}}
    :main-opts ["-m" "alc.index-defs.ctags"]
   }
```

To create an index file, from the relevant project directory:

```
$ clj -A:etags
```

or

```
$ clj -A:ctags
```

Note that:

* Created files and directories are: `tags` / `TAGS` and `.alc-id` (in project directory).  The `.alc-id` directory should contain uncompressed jar files (without class files) that were among the project's dependencies.

* To re-index:

  * Delete `tags` / `TAGS` and `.alc-id` -OR-

  * Pass `'{:overwrite true}'`to the `clj` command, e.g. `clj -A:etags '{:overwrite true}'`

* Sticking to generating just the one you need may work better as some editor setups try to do "clever" things and end up reading in the wrong file.

## Use With Specific Editors

* [Atom](doc/atom.md) - TLDR; Atom itself seems to need fixing
* [Emacs](doc/emacs.md)
* [vim](doc/vim.md)
* [VSCode](doc/vscode.md)

## Technical Details

Curious about some [technical details](doc/technical-details.md)?  No?  That's why they are not on this page :)

## Notes

Tree-sitter looks promising, especially from the perspective of indexing speed -- both initial and incremental updtaing.

## Related

* [clindex](https://github.com/jpmonettas/clindex)
* [lein-codeindex](https://github.com/sanel/lein-codeindex)

## Acknowledgments

* borkdude - clj-kondo and more
* PEZ - VSCode assistance
* Saikyun - discussion and testing
* taylorwood - clj.native-image internals
