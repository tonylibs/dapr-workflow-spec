# Changelog

## [1.4.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-controller-v1.3.0...dws-controller-v1.4.0) (2026-11-15)


### Features

* **controller:** stamp a trigger ConfigMap for scheduled versions ([7e726f7](https://github.com/tonylibs/dapr-workflow-spec/commit/7e726f746199959feffeb11fd1b4c46dc1c406fb))
* **controller:** validate schedule time triggers at submit ([55e08b1](https://github.com/tonylibs/dapr-workflow-spec/commit/55e08b1605b260942be168349b66e52a9a6f9cda))
* scheduling Phase 6a — time triggers (every/cron/after) via dws-schedule-time ([6046db6](https://github.com/tonylibs/dapr-workflow-spec/commit/6046db6664781aa02e5326730c8c09a038c7674b))


### Bug Fixes

* **controller:** disable the jsii package cache in the runtime image ([8df833a](https://github.com/tonylibs/dapr-workflow-spec/commit/8df833abb66d3b0182e217d78fd70c45287be851))


### Documentation

* **openspec:** complete phase-6a controller tasks, drain is an annotation ([a80f40a](https://github.com/tonylibs/dapr-workflow-spec/commit/a80f40ad9e3816bd038a69d16e7fe6d218b9356b))

## [1.3.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-controller-v1.2.1...dws-controller-v1.3.0) (2026-11-01)


### Features

* **controller:** add DWS_LOG_FORMAT json logging with scrubber and contract test ([5220cd4](https://github.com/tonylibs/dapr-workflow-spec/commit/5220cd4ab8c6166ae145f515c38327dfa5d9eab0))
* **controller:** stamp DWS_LOG_FORMAT=json on instrumented orchestrators ([474e48f](https://github.com/tonylibs/dapr-workflow-spec/commit/474e48fe0c2204158cfff27263ce3ce288a2cba7))


### Bug Fixes

* **controller:** keep document URL query and userinfo out of OpenAPI fetch errors ([60726b0](https://github.com/tonylibs/dapr-workflow-spec/commit/60726b0cfecb7d07b852f8b63f55d59f1fdc4fe7))


### Documentation

* **openspec:** propose observability-phase4-log-trace-correlation ([1488723](https://github.com/tonylibs/dapr-workflow-spec/commit/14887238840cba7cb9c32a30cfe5feabdba241b5))

## [1.2.1](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-controller-v1.2.0...dws-controller-v1.2.1) (2026-10-11)


### Bug Fixes

* **controller:** set daprd metrics port to 9095 on Knative steps ([18b4dae](https://github.com/tonylibs/dapr-workflow-spec/commit/18b4dae9346833ecc02d67e4226a9d376ab63847))

## [1.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-controller-v1.1.1...dws-controller-v1.2.0) (2026-10-11)


### Features

* **controller:** render one merged Dapr Configuration per workload ([85e553c](https://github.com/tonylibs/dapr-workflow-spec/commit/85e553c75d774a4957f8ba95fe5f55e4fbb73e51))

## [1.1.1](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-controller-v1.1.0...dws-controller-v1.1.1) (2026-10-10)


### Bug Fixes

* **controller:** grant orchestrator pods dedicated RBAC and config store access ([f5e0b15](https://github.com/tonylibs/dapr-workflow-spec/commit/f5e0b15b09a23975a027204767380fa023ee0a24))

## [1.1.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-controller-v1.0.0...dws-controller-v1.1.0) (2026-10-10)


### Features

* **controller:** instrument the compiled orchestrator when observability is on ([8c7f59c](https://github.com/tonylibs/dapr-workflow-spec/commit/8c7f59cb4cd570072a3e949974b9b5b595199627))
* **controller:** refactor code ([2800d43](https://github.com/tonylibs/dapr-workflow-spec/commit/2800d4323f90e8b326b9fed21efc81dbfbcb8a07))


### Bug Fixes

* **controller:** restore single-line WARN and stable orchestrator env order ([82a6675](https://github.com/tonylibs/dapr-workflow-spec/commit/82a6675574fc649cc0ef27159ffecc1521196e94))
