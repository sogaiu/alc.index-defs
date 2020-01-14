# alc.index-defs

## Purpose

Create index files for source code...including dependencies.

Currently supported output formats are [tags](http://ctags.sourceforge.net/FORMAT) (vi, et. al) and [TAGS](https://en.wikipedia.org/wiki/Ctags#Etags_2) (Emacs).

This is a means to an end -- the primary goal is to be able to "jump to definition".  In some cases one might derive additional benefits, for example, improved completion or renaming of identifiers.

## Prerequisites

* clj / clojure

## Use Cases

* Want to easily navigate source without firing up a REPL?  For example, just downloaded a project and don't have it running?  Or may be sometimes you don't want to have to start a REPL just to examine some code paths?  [1]

* The editor setup you're using doesn't support jumping to definitions within dependencies?

* The editor setup you're using can't account for the namespace portion of identifiers?

* The editor setup you're using doesn't handle enough of the identifiers in your code base, possibly because it doesn't know how to appropriately determine identifiers?

[1] Note that the tool that's typically used to resolve / fetch dependencies for the project is still needed.

## Limitations

* The index file generation is not fast in human perception terms.  That means this method is not particularly suited to tracking changes to source code in real time.

* Identifiers are determining statically (using [clj-kondo](https://github.com/borkdude/clj-kondo)) so there are certain types of idenitifers that will not be determinable.

* Identifiers of the interop persuasion are not handled.  Would be nice, huh?

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

What you need to do to use the file will vary by the editor.

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
    :extra-deps {sogaiu/alc.index-defs {:git/url "https://github.com/sogaiu/alc.index-defs"
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
    :extra-deps {sogaiu/alc.index-defs {:git/url "https://github.com/sogaiu/alc.index-defs"
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

### How Does It Work?

All of the heavy-lifting is done by [clj-kondo's analysis capability](https://github.com/borkdude/clj-kondo/analysis).  

To determine exactly what bits of code are to be analyzed, a classpath is required.  By default, the files `shadow-cljs.edn`, `deps.edn`, `project.clj`, and `build.boot` are searched for.  If any of these is found, the command that uses it (e.g. `shadow-cljs`, `clj`, `lein`, or `boot`) is invoked to compute a classpath.

Sometimes auto-detection doesn't produced the desired result, in which case one of the following options can be specified:

* `:method` - one of `:shadow-cljs`, `:clj`, `:lein`, or `:boot`
* `:paths` - a classpath string, e.g.`"src:/home/user/.m2/repository/com/cognitect/transit-java/0.8.337/transit-java-0.8.337.jar"` or `"src/clj/clojure"`
* `:cp-command` - a vector describing a command to invoke to determine a classpath, e.g. `["yarn" "--slient" "shadow-cljs" "classpath"]`

_N.B. The current command line interface is mostly based on passing in a string representation of a Clojure map.  This might change._

### About Those Formats...

`tags` and `TAGS` don't quite support the same sort of functionality.

For example, Emacs uses `TAGS` in such a way so that certain changes don't affect the ability to still locate the identifier in question (e.g. extra code being added doesn't tend to cause lookups of previously existing identifiers to fail).  I haven't seen an editor setup that uses `tags` that does this sort of thing.

`tags`-file lookups may be faster though as many setups refuse to work without the `tags` file being sorted -- which enables a binary search.  Haven't actually measured though, so who knows?

In general, producing `TAGS` is more involved, but not all of the fields need to be "filled in".  Finding adequate docs for it was harder than for `tags`, but examining source and testing were sufficient to determine enough details to make something that seems to work ok.

## Notes

Tree-sitter looks like a promising thing, especially from the perspective of indexing speed -- both initial and incremental updtaing.

## Acknowledgments

* borkdude - clj-kondo and more
* PEZ - VSCode assistance
* Saikyun - discussion and testing
