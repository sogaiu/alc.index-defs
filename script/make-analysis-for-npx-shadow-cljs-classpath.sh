#! /bin/sh

clj-kondo \
  --lint `npx --quiet shadow-cljs classpath` \
  --config '{:output {:analysis true :format :edn :canonical-paths true}}' \
> clj-kondo-analysis-full-paths.edn
