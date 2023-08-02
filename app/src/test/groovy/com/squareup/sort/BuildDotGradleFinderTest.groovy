package com.squareup.sort


import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static com.google.common.truth.Truth.assertThat

class BuildDotGradleFinderTest extends Specification {

    @TempDir
    private Path dir

    def setup() {
        dir.resolve("build.gradle.kts").toFile().createNewFile()
        dir.resolve("app").toFile().mkdirs()
        dir.resolve("app/build.gradle").toFile().createNewFile()
    }

    def "can find build script files inside current directory"() {
        given: 'A BuildDotGradleFinder using the current directory'
        def finder = new BuildDotGradleFinder(
                /*path = */ dir,
                /*searchPaths =*/ ['.'],
                /*skipHiddenAndBuildDirs = */ true,
        )

        when: 'Searching for Gradle build files'
        def results = finder.buildDotGradles

        then: 'Gradle build files are found'
        assertThat(results).containsExactly(
                dir.resolve("build.gradle.kts"),
                dir.resolve("app/build.gradle")
        )
    }

}
