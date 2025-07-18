# Pipeline Lib ORAS Plugin

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/pipeline-lib-oras-plugin/main)](https://ci.jenkins.io/job/plugins/job/pipeline-lib-oras-plugin-plugin/)
[![Coverage](https://ci.jenkins.io/job/Plugins/job/pipeline-lib-oras-plugin/job/main/badge/icon?status=${instructionCoverage}&subject=coverage&color=${colorInstructionCoverage})](https://ci.jenkins.io/job/Plugins/job/pipeline-lib-oras-plugin/job/main)
[![LOC](https://ci.jenkins.io/job/Plugins/job/pipeline-lib-oras-plugin/job/main/badge/icon?job=test&status=${lineOfCode}&subject=line%20of%20code&color=blue)](https://ci.jenkins.io/job/Plugins/job/pipeline-lib-oras-plugin/job/main)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/pipeline-lib-oras.svg)](https://plugins.jenkins.io/pipeline-lib-oras/)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/pipeline-lib-oras-plugin.svg?label=changelog)](https://github.com/jenkinsci/pipeline-lib-oras-plugin/releases/latest)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/pipeline-lib-oras-plugin.svg)](https://github.com/jenkinsci/pipeline-lib-oras-plugin/graphs/contributors)

## Introduction

This plugin allow to fetch jenkins libraries stored into an OCI compliant registry (ORAS).

> [!WARNING]
> The ORAS Java SDK is currently in **alpha** state and might impact the stability of this plugin.
>
> It's configuration and APIs might change in future releases

<p align="left">
<a href="https://oras.land/"><img src="https://oras.land/img/oras.svg" alt="banner" width="200px"></a>
</p>

## Getting started

When configuring a pipeline job, just select the "Pipeline library from ORAS" option in the "Library" section.

Credentials are optional if using an unsecured registry. Otherwise you need to provide a username/password credential.

![config.png](docs/config.png)

In order to consume a library you need to ensure to set the following artifact type `application/vnd.jenkins.lib.manifest.v1+json`

You can push such an artifact using the [ORAS CLI](https://oras.land/docs/commands/oras_push):

```bash
oras push localhost:5000/lib:latest \
--artifact-type application/vnd.jenkins.lib.manifest.v1+json \
--annotation org.opencontainers.image.revision=sha1:$(git rev-parse HEAD) \
--annotation org.opencontainers.image.source=$(git config --get remote.origin.url) my-lib
```

When pushing a directory to oras, the `src`, `vars`, and `resources` can be set under a unique directory name, for example `my-lib` in the above command.

You will see then on the logs the digest of the library

![log.png](docs/log.png)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

