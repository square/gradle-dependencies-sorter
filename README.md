# gradle-dependencies-sorter

This JVM CLI app and companion Gradle plugin can sort the dependencies of a `build.gradle[.kts]` script.

## Usage

### CLI

```shell
./path/to/sort <path(s) that contain build.gradle[.kts] scripts>

# for example, use this to sort the full repo
./path/to/sort .

# for example, use this to sort a sub-tree
./path/to/sort features

# for example, use this to sort a single file
./path/to/sort my/app/build.gradle[.kts]

# Check sort status
./path/to/sort -m check <paths as above>
./path/to/sort --mode check <paths as above>
```

### Gradle plugin

#### Sort it

```shell
./gradlew :my:app:sortDependencies

# Identical to the above
./gradlew :my:app:sortDependencies --mode sort
```

#### Check it

```shell
./gradlew :my:app:sortDependencies --mode check
```

## Test it

```shell
./gradlew check
```

## Build it

### Publish everything to maven local

```shell
./gradlew publishToMavenLocal
```

This will push all of this project's artifacts to `~/.m2/repository/`.

### Build zip distribution

```shell
./gradlew :app:shadowDistZip
```

This creates a zip file of the distribution at `app/build/distributions/`. This archive may be installed anywhere you 
like.

### Install CLI app

```shell
./gradlew :app:installShadowDist
```

This will install the app to `app/build/install/app-shadow/`.

## Pre-OSS Contributors
* https://github.com/autonomousapps
* https://github.com/darshanparajuli
* https://github.com/holmes

## License

    Copyright 2022 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
