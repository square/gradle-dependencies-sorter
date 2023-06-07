# gradle-dependencies-sorter

## Version 0.3

* Replace `--quiet` with `--verbose` flag. The CLI and gradle plugin now run in quiet mode by default, and can be made verbose with `--verbose`.
* Split Gradle tasks into `sortDependencies` (which sorts) and `checkSortDependencies` (which checks that dependencies are sorted). The latter is automatically added as a dependency of the `check` lifecycle task too.
* New: Support `add(configuration, dependency)` notation. Thanks to [@kozaxinan](https://github.com/kozaxinan) for the contribution.
* New: Support function call dependency declarations like `gradleApi()`.
* New: Support for Map-syntax version of `project(...)`, such as `project(path: ':kool', configuration: 'config')`. Thanks to [@jamesonwilliams](https://github.com/jamesonwilliams) for the contribution.
* Fix: Fix path traversal ISE in `FileTreeIterator.hasNext()`.
* Fix: Close logger after invocation and print location if verbose or errors reported.
* Fix: Add thread safety for log file creation. Thanks to [@catherine-chi](https://github.com/catherine-chi) for the contribution.
* Fix: Tolerate `project()` declarations in non-`dependencies` text blocks. Thanks to [@jamesonwilliams](https://github.com/jamesonwilliams) for the contribution.
* Docs: Add badge link to central in README. Thanks to [@JakeWharton](https://github.com/JakeWharton) for the contribution.
* Docs: Document location of the fat jar to download in README.
* Docs: Add version badge to README. Thanks to [@SimonMarquis](https://github.com/SimonMarquis) for the contribution.

## Version 0.2
* Fixes and improvements.

## Version 0.1
* Initial release.
