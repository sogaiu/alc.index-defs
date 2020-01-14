# vim (and derivatives?)

## Setup

1. Ensure some appropriate `deps.edn` has been [edited appropriately](../README.md#general-setup-and-use).


2. Index a Clojure code base:

   ```
   $ cd /home/alice/a-clj-proj-dir
   $ clj -A:ctags
   ```

## Try it out

1. Open a Clojure file from the indexed project

2. Put your cursor within an identifier and press `Ctrl-]`

(`Ctrl-O` seems to go back.)
