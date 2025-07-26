package com.squareup.sort

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static com.google.common.truth.Truth.assertThat

class CustomBuildDotGradleNameFinderTest extends Specification {

    @TempDir
    private Path dir

    def setup() {
        dir.resolve("build.gradle.kts").toFile().createNewFile()
        dir.resolve("app").toFile().mkdirs()
        dir.resolve("app/app.gradle.kts").toFile().createNewFile()
    }

    def "can find build script files inside current directory when named after module"() {
        given: 'A BuildDotGradleFinder using the current directory'
        def finder = new BuildDotGradleFinder(
                /*path = */ dir,
                /*searchPaths =*/ [dir],
                /*skipHiddenAndBuildDirs = */ true,
        )

        when: 'Searching for Gradle build files'
        def results = finder.buildDotGradles

        then: 'Gradle build files are found'
        assertThat(results).containsExactly(
                dir.resolve("build.gradle.kts"),
                dir.resolve("app/app.gradle.kts")
        )
    }

}
