# Changelog

## 0.1.0 (2026-11-26)


### Features

* **jq:** compile once and evaluate with v1 result and truthiness ([f3b8506](https://github.com/tonylibs/dapr-workflow-spec/commit/f3b850634491a6de9636158969964ca74ed9287c))
* **jq:** read referenced secrets from the Dapr secrets API and redact them ([541364c](https://github.com/tonylibs/dapr-workflow-spec/commit/541364c7dded0a8b702b7e604977d2854712ea4d))
* **jq:** reject blocked builtins and dynamic $secrets by AST walk ([2dbfde5](https://github.com/tonylibs/dapr-workflow-spec/commit/2dbfde5096a373fbb18df0f2c26271306eeb8b67))
* **jq:** scaffold dws-jq and pin its configuration contract ([d3bd95d](https://github.com/tonylibs/dapr-workflow-spec/commit/d3bd95d7b52e4d7db4c75df116de3444993151e5))
* **jq:** serve POST /run and gate readiness on startup checks ([33d2c2f](https://github.com/tonylibs/dapr-workflow-spec/commit/33d2c2fdecff4d55ffb6b69987e3a6bde85d2db4))
* **jq:** write the DWS structured log format ([93bf31d](https://github.com/tonylibs/dapr-workflow-spec/commit/93bf31d3641efac8e189da84102554514937e946))


### Bug Fixes

* **jq:** bind only the exact value key and refuse the bulk secret route ([ed49bc8](https://github.com/tonylibs/dapr-workflow-spec/commit/ed49bc89fcc056fb189cb62494b8fe1c86031412))
* **jq:** encode results with gojq.Marshal for jq-identical output ([79031ce](https://github.com/tonylibs/dapr-workflow-spec/commit/79031ce5f40dbdc0b10d26838aebcfb5975d6e3b))
* **jq:** enforce exact request fields and the 4 MiB cap ([8559f8e](https://github.com/tonylibs/dapr-workflow-spec/commit/8559f8e39e4c1a05964d40d439bd8ba7d0bc72d8))
* **jq:** name site and expression in configuration startup errors ([f2e42b2](https://github.com/tonylibs/dapr-workflow-spec/commit/f2e42b23a50c9f77405fc08d17460a5298074750))
* **jq:** name the expression on secret-read startup failures ([4942e66](https://github.com/tonylibs/dapr-workflow-spec/commit/4942e66fcec9cecf20e0345b89123f51189f9871))
* **jq:** redact every attribute value in text-mode logs ([2d5bda4](https://github.com/tonylibs/dapr-workflow-spec/commit/2d5bda41a4fcd318d5a289330b237135afa4abaa))
* **jq:** redact secret prefixes in gojq's truncated value previews ([2d55365](https://github.com/tonylibs/dapr-workflow-spec/commit/2d55365d6d055461b06037a245b22ebc0d1bad7c))
* **jq:** redact secrets in gojq's escaped form and reject dot-segment names ([152dc47](https://github.com/tonylibs/dapr-workflow-spec/commit/152dc4703b504534afb256b9023009503f38b43b))
* **jq:** report the last sidecar failure when the secret fetch bound expires ([ec16280](https://github.com/tonylibs/dapr-workflow-spec/commit/ec162809ea0858f8ebc7db104bb4cf8f8d6ca475))
* **jq:** retry client timeouts and accept null variables ([6d3996a](https://github.com/tonylibs/dapr-workflow-spec/commit/6d3996a1a94f8011c2dec4889031ecfa2188c9b8))
