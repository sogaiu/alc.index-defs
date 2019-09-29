#! /bin/sh

clj-kondo \
  --lint `clj -Spath` \
  --config '{:output {:analysis true :format :edn :canonical-paths true}}' \
> clj-kondo-analysis-full-paths.edn
