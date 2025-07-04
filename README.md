# layers

<img src="./assets/layers.logo.svg" width="64px" alt="Layers Logo" align="right"/>

`layers` is a loose collection of generic components for building services and is heavily based
on [Apache Pekko](https://github.com/apache/pekko).

## Setup

```
libraryDependencies ++= Seq(
  "io.github.sndnv" %% "layers" % "<version>",
  ...
)
```

## Components and Usage

> Examples of their usage can be seen in the tests of each component.

### [`api`](lib/src/main/scala/io/github/sndnv/layers/api)

Directives for logging/metrics and discarding entities, JSON formats and matchers.

### [`persistence`](lib/src/main/scala/io/github/sndnv/layers/persistence)

Generic data store traits with migration support.

### [`security`](lib/src/main/scala/io/github/sndnv/layers/security)

JWT-based authenticators, JWK providers, OAuth clients and TLS helper classes.

### [`service`](lib/src/main/scala/io/github/sndnv/layers/service)

Service persistence and bootstrap providers.

### [`telemetry`](lib/src/main/scala/io/github/sndnv/layers/telemetry)

Collectors and providers for analytics information and metrics.

### [`testing`](testing/README.md)

Helper classes and functions for testing.

## Development

Refer to the [DEVELOPMENT.md](DEVELOPMENT.md) file for more details.

## Contributing

Contributions are always welcome!

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details.

## Versioning

We use [SemVer](http://semver.org/) for versioning.

## License

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details

> Copyright 2025 https://github.com/sndnv
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.
