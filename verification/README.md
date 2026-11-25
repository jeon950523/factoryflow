# Independent verification

Node 22, no app imports or third-party dependencies. Run `node --test verification/oracle.test.mjs` for oracle controls. The live test is destructive to its own new fixture data and must only run on disposable CI MySQL; use the dedicated workflow. Never run against production. See TASK-025 and ADR-016.
