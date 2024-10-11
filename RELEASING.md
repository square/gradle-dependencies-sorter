Release procedure for gradle-dependencies-sorter

1. Update CHANGELOG
1. Update README if needed
1. Bump version number in `gradle.properties` to next stable version (removing the `-SNAPSHOT` suffix).
1. `git commit -am "Prepare for release x.y."`
1. Publish the snapshot to Maven Central by invoking the `publish` action on github.
1. `git tag -a vx.y -m "Version x.y."`
1. Update version number `gradle.properties` to next snapshot version (x.y-SNAPSHOT)
1. `git commit -am "Prepare next development version."`
1. `git push && git push --tags`
1. (Optional) Follow instructions in console output to release from Maven Central's staging repo.
   This step is now automated via the `:promote` task, and should only be necessary if that task
   fails.
