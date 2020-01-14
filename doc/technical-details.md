# Technical Details

## How Does It Work?

All of the heavy-lifting is done by [clj-kondo's analysis capability](https://github.com/borkdude/clj-kondo/analysis).

To determine exactly what bits of code are to be analyzed, a classpath is required.  By default, a search is performed for the files `shadow-cljs.edn`, `deps.edn`, `project.clj`, and `build.boot`.  The first one located is used to decide which command to employ for classpath computation.  The corresponding commands are: `shadow-cljs`, `clj`, `lein`, and `boot`.

Sometimes auto-detection doesn't produce the desired result, in which case one of the following options can be specified:

* `:method` - one of `:shadow-cljs`, `:clj`, `:lein`, or `:boot`
* `:paths` - a classpath string, e.g.`"src:/home/user/.m2/repository/com/cognitect/transit-java/0.8.337/transit-java-0.8.337.jar"` or `"src/clj/clojure"`
* `:cp-command` - a vector describing a command to invoke to determine a classpath, e.g. `["yarn" "--slient" "shadow-cljs" "classpath"]`

_N.B. The current command line interface is mostly based on passing in a string representation of a Clojure map.  This might change._

## About Those Formats...

`tags` and `TAGS` are similar conceptually but differ in various ways.

The [Ctags Wikipedia article](https://en.wikipedia.org/wiki/Ctags#Tags_file_formats) gives relatively straight-forward (if somewhat simplified) descriptions of each.  Enough to suggest this sort of project might be possible, but not quite enough on its own :)

Emacs uses `TAGS` in such a way so that certain changes don't affect the ability to still locate the identifier in question (e.g. extra code being added doesn't tend to cause lookups of previously existing identifiers to fail).  (I don't think `tags` files store the type of information one could use to achieve this end well.)

Some editors setups won't work without a sorted `tags`-file.

`TAGS` groups index information by file, while `tags` is essentially one big list.

`TAGS` supports the notion of "including" files, while AFAIK, `tags` doesn't.

In general, producing `TAGS` is more involved.  It could be even more involved than what this project does, but thankfully, not all of the fields need to be "filled in" -- at least not for things to work in Emacs.

Finding adequate docs for `TAGS` was harder than for `tags`, but examining source and testing were sufficient to determine enough details to make something that seems to work ok.

