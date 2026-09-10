# Changelog

## [0.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-admin-v0.1.0...dws-admin-v0.2.0) (2026-09-10)


### Features

* consolidate dws-admin on a single Dapr app port ([fa1ed09](https://github.com/tonylibs/dapr-workflow-spec/commit/fa1ed095c335cde41c203fb0bfde46b8bc5ef39d))
* detect duplicate task names across nested definition bodies ([6c5ec61](https://github.com/tonylibs/dapr-workflow-spec/commit/6c5ec61062dbff7d0c96dff1662d0d513775c81c))
* **dws-admin:** read-only REST API for workflows and instances ([f710ec9](https://github.com/tonylibs/dapr-workflow-spec/commit/f710ec9a1b9f2924c553cb28e3bdce26abc1df47))
* **dws-admin:** send CORS headers on the read API ([06a39a3](https://github.com/tonylibs/dapr-workflow-spec/commit/06a39a35e9cce771c09c4611741ec85d82a70d90))
* **dws-admin:** SSE push API for instance and task status changes ([cb8012b](https://github.com/tonylibs/dapr-workflow-spec/commit/cb8012b7c7f4875cd2a8eb0bf9899dbd1a5a44a0))
* **dws-console:** live status updates for running instances ([89538b4](https://github.com/tonylibs/dapr-workflow-spec/commit/89538b44363dac2387ee2989303cc4c3781d7f80))
* expose POST /definitions/validate on dws-admin ([566f338](https://github.com/tonylibs/dapr-workflow-spec/commit/566f33854405b8053fa4b937ddfa8eb6d819c37a))
* validate definitions against the vendored DSL schema ([aa95e2d](https://github.com/tonylibs/dapr-workflow-spec/commit/aa95e2d0c902e6a1ffbd617578a8ac2502ce2a6a))


### Bug Fixes

* complete console editor live acceptance ([0eb7f6a](https://github.com/tonylibs/dapr-workflow-spec/commit/0eb7f6aace1eebb4a2b1c2a4aa0571d5002b4bcd))
* give POST /definitions/validate the raw bytes for JSON too ([2d92ba8](https://github.com/tonylibs/dapr-workflow-spec/commit/2d92ba84a09ac3099116665f382b71c5c6945df0))
* lift the definition body cap to the documented 1 MiB ([7e79d20](https://github.com/tonylibs/dapr-workflow-spec/commit/7e79d2002f01c98a5844678aee171e0f57df0405))
