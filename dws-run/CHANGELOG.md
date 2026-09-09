# Changelog

## [0.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-run-v0.1.0...dws-run-v0.2.0) (2026-09-09)


### Features

* **dws-run:** add POST /run and GET /healthz with 502 error mapping ([2bc3803](https://github.com/tonylibs/dapr-workflow-spec/commit/2bc38034bea734f5108070bb1072a19f5244832a))
* **dws-run:** render arguments as shell flags and script bindings ([abe78ec](https://github.com/tonylibs/dapr-workflow-spec/commit/abe78ec91dc30b913a4b05bbe1f366de7f43619d))
* **dws-run:** select result by RETURN with exit-code semantics ([77583e6](https://github.com/tonylibs/dapr-workflow-spec/commit/77583e668c9f774b3451f56dbb4a237e692a9b8a))
* **dws-run:** shape output with JSON fallback and merge support ([29b4cf8](https://github.com/tonylibs/dapr-workflow-spec/commit/29b4cf8c8e223279669089f8f689ce6e2e3e888b))
* implement dws-run component for run.shell and run.script tasks ([d987abd](https://github.com/tonylibs/dapr-workflow-spec/commit/d987abda732d1548341f5ab481f0643561daff04))
* **run:** run as a Dapr Workflow activity worker ([557278c](https://github.com/tonylibs/dapr-workflow-spec/commit/557278c7c5cd3a267dadc1c336c829c46f423a14))


### Bug Fixes

* **dws-run:** distinguish timeout/cancel from a genuine non-zero exit ([94dcf26](https://github.com/tonylibs/dapr-workflow-spec/commit/94dcf2638850a46e4cfad1728646ac379752cd1d))
* **dws-run:** reject bad script argument identifiers at startup, not on first invocation ([1ec15df](https://github.com/tonylibs/dapr-workflow-spec/commit/1ec15df1d22bb9abc35d60f2f7314155ddcff87d))
* **dws-run:** reject reserved keywords/internal names in argument bindings ([c10a0c8](https://github.com/tonylibs/dapr-workflow-spec/commit/c10a0c82ff92354bedcbcdbcb1676db33a8760ef))
* **dws-run:** stop normalize() from truncating large integer arguments ([cead846](https://github.com/tonylibs/dapr-workflow-spec/commit/cead8461005761f93adce36767ab75bde1ab155f))
