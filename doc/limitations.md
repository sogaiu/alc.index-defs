# Limitations

* The index file generation is not fast in human perception terms.  That means this method is not particularly suited to tracking changes to source code in real time.

* Identifiers are determining statically (using [clj-kondo](https://github.com/borkdude/clj-kondo)) so there are certain types of idenitifers that will not be determinable.

* Identifiers of the interop persuasion are not handled.  Would be nice, huh?
