# Changelog

## [0.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-console-v0.1.0...dws-console-v0.2.0) (2026-09-09)


### Features

* add spec-validation and dry-run preview calls to the admin client ([22b9dec](https://github.com/tonylibs/dapr-workflow-spec/commit/22b9decdc6a6c8260ca256ab46a5f62d09dda515))
* authenticate every dws-console admin transport ([3e6c4e3](https://github.com/tonylibs/dapr-workflow-spec/commit/3e6c4e365c297d087b26fba674db228aea2e5a12))
* **console:** add workflow definition editor ([be82978](https://github.com/tonylibs/dapr-workflow-spec/commit/be829786a8ff9a9e358192ccc6fcf8856038bd59))
* **console:** import persisted definition drafts ([c987591](https://github.com/tonylibs/dapr-workflow-spec/commit/c98759105e233a57cb3e010b2194d26f3702df10))
* **dws-admin:** send CORS headers on the read API ([06a39a3](https://github.com/tonylibs/dapr-workflow-spec/commit/06a39a35e9cce771c09c4611741ec85d82a70d90))
* **dws-console:** containerize and build the image in CI ([e1e57f5](https://github.com/tonylibs/dapr-workflow-spec/commit/e1e57f5ec178258ce1159e4a420cc3dcf5a4dcf9))
* **dws-console:** implement observability UI mockups ([e531019](https://github.com/tonylibs/dapr-workflow-spec/commit/e531019c0ff0e61d399b2fdabb924db6108cd146))
* **dws-console:** live status updates for running instances ([89538b4](https://github.com/tonylibs/dapr-workflow-spec/commit/89538b44363dac2387ee2989303cc4c3781d7f80))
* **dws-console:** wire read routes to the live dws-admin API ([497d7c8](https://github.com/tonylibs/dapr-workflow-spec/commit/497d7c8cedb17cd00d7826c9b2883e18a5b41534))
* preview a definition's deployment plan before submitting it ([2cc9f2d](https://github.com/tonylibs/dapr-workflow-spec/commit/2cc9f2dfd1b5b0519c23d1921eee2aaeceb7970d))


### Bug Fixes

* complete console editor live acceptance ([0eb7f6a](https://github.com/tonylibs/dapr-workflow-spec/commit/0eb7f6aace1eebb4a2b1c2a4aa0571d5002b4bcd))
* **console:** address definition editor review feedback ([93555f7](https://github.com/tonylibs/dapr-workflow-spec/commit/93555f793c5d6ecc949a12735b9e34e61bfcfcd3))
* **console:** refresh generated route tree ([5eedd6e](https://github.com/tonylibs/dapr-workflow-spec/commit/5eedd6e3ca991e48f00afc410049b8a33fdd5c14))
* **console:** regenerate route tree ([0f6f3f6](https://github.com/tonylibs/dapr-workflow-spec/commit/0f6f3f6ce25c67c400585f98527ea95250bd3bf9))
* **console:** restore routeTree.gen.ts to tsr generate output ([f87b550](https://github.com/tonylibs/dapr-workflow-spec/commit/f87b5508f22146026a4e308fcfab3002f8296beb))
* **console:** synchronize npm lockfile ([01cdb20](https://github.com/tonylibs/dapr-workflow-spec/commit/01cdb201a052778756514e914a74b1aefdc67dad))
* **console:** synchronize npm lockfile ([0187626](https://github.com/tonylibs/dapr-workflow-spec/commit/0187626d5b15c0ccc3a515d6ac1353b3fe287d4a))
* **dws-console:** commit canonical tsr-generated route tree ([83aa762](https://github.com/tonylibs/dapr-workflow-spec/commit/83aa76279a425ec6d9a10d46fd7b1e3de6f97386))
* **dws-console:** commit tsr generate's route tree output ([42814c6](https://github.com/tonylibs/dapr-workflow-spec/commit/42814c67ce22559606bafa6a6b9330df72b1254f))
* **dws-console:** install TanStack Table v9 to match the v9 API migration ([98870a6](https://github.com/tonylibs/dapr-workflow-spec/commit/98870a6c3bbe87ec3b459cf04d84fc0902ed2142))
* **dws-console:** sync package-lock.json and commit tsr's route tree ([29ffbc5](https://github.com/tonylibs/dapr-workflow-spec/commit/29ffbc5f7f618422a963ae68edb8695bb644caac))
* **dws-console:** use the status vocabulary dws-admin actually stores ([d30c36f](https://github.com/tonylibs/dapr-workflow-spec/commit/d30c36f93852fdb00b7a87dcd311b327c2c18749))
* record live dex phase 1 validation ([c216038](https://github.com/tonylibs/dapr-workflow-spec/commit/c21603800b5eee222104c56b60e0f3e5f4ef61d8))
* repair API gateway CI checks ([4182839](https://github.com/tonylibs/dapr-workflow-spec/commit/4182839b8777d218d080597e682e8b93fa9b0939))
* retire preview outcomes when a definition is submitted ([539f9da](https://github.com/tonylibs/dapr-workflow-spec/commit/539f9dad9b9cc727b4b3c22c72ad85dceac3da6b))
* stabilize API gateway CI assertions ([402641e](https://github.com/tonylibs/dapr-workflow-spec/commit/402641e83a9b2d35f35b57420dd72ae64e5ba4a6))
