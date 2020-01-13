#! /bin/sh

clj-kondo \
  --lint "$1" \
  --config '{:output {:analysis true :format :edn :canonical-paths true}}' \
> clj-kondo-analysis-full-paths.edn
