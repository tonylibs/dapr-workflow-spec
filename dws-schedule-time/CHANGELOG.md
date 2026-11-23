# Changelog

## [0.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-schedule-time-v0.1.0...dws-schedule-time-v0.2.0) (2026-11-23)


### Features

* **schedule-time:** log successful trigger starts at Information ([82b001f](https://github.com/tonylibs/dapr-workflow-spec/commit/82b001f091443aff5139f4b7270b34951dce42ab))


### Bug Fixes

* **schedule-time:** default Microsoft.AspNetCore logging to Warning ([218903c](https://github.com/tonylibs/dapr-workflow-spec/commit/218903c6dee503ee6050fa0c6f325686ef8cb4a8))

## 0.1.0 (2026-11-14)


### Features

* **schedule-time:** derive deterministic trigger instance IDs and fire keys ([359c331](https://github.com/tonylibs/dapr-workflow-spec/commit/359c331d91caeeb7677018a87954f8ec8c66b780))
* **schedule-time:** parse trigger configs, resolve active triggers and map job specs ([770f16f](https://github.com/tonylibs/dapr-workflow-spec/commit/770f16f2e583e86209c74670e7daa786574a1cf8))
* **schedule-time:** reconcile jobs from the cluster and run the after lifecycle ([b0695f0](https://github.com/tonylibs/dapr-workflow-spec/commit/b0695f02b155d69cfe62588785ef81b77b42c344))
* **schedule-time:** scaffold dws-schedule-time package with /healthz and DWS_LOG_FORMAT ([e7b887d](https://github.com/tonylibs/dapr-workflow-spec/commit/e7b887d76af4abae99e51c7fbc6528656be45963))
* **schedule-time:** scaffold dws-schedule-time package with JSON log formatter ([de05765](https://github.com/tonylibs/dapr-workflow-spec/commit/de057657c29ca53290880853b0cc37f6fa9b5832))
* **schedule-time:** start every/cron fires through a state-store claim ([a9891c8](https://github.com/tonylibs/dapr-workflow-spec/commit/a9891c8e9255552c545b11c7548c71b03015363b))
* **schedule-time:** start workflows through orchestrator service invocation ([71b09c3](https://github.com/tonylibs/dapr-workflow-spec/commit/71b09c3cbf5ef6bf102119b5b9cd663b72615db7))
* scheduling Phase 6a — time triggers (every/cron/after) via dws-schedule-time ([6046db6](https://github.com/tonylibs/dapr-workflow-spec/commit/6046db6664781aa02e5326730c8c09a038c7674b))


### Bug Fixes

* **schedule-time:** hold job callbacks until the first cluster list ([d598335](https://github.com/tonylibs/dapr-workflow-spec/commit/d598335b55c28cc97216cde2e5e0e3544a7a537d))
* **schedule-time:** keep test build output out of the app project's globs ([13b46c8](https://github.com/tonylibs/dapr-workflow-spec/commit/13b46c8aae27c5ca920e9bfdac1e89c94cea82c5))
* **schedule-time:** send the Dapr API token on service-invocation starts ([1151103](https://github.com/tonylibs/dapr-workflow-spec/commit/1151103547940c1041cece6d73055d88cee47aac))

## Changelog
