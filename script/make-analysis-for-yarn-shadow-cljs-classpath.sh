#! /bin/sh

clj-kondo \
  --lint `yarn --silent shadow-cljs classpath` \
  --config '{:output {:analysis true :format :edn :canonical-paths true}}' \
> clj-kondo-analysis-full-paths.edn
