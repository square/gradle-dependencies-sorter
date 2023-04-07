package com.squareup.grammar

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTreeWalker
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import static com.google.common.truth.Truth.assertThat

final class GradleGroovyScriptSpec extends Specification {

  @TempDir
  Path dir

  def "can parse dependencies"() {
    given:
    def sourceFile = dir.resolve('build.gradle')
    Files.writeString(
      sourceFile,
      '''\
          import foo
          import static bar;

          plugins {
            id 'foo'
          }

          repositories {
            google()
            mavenCentral()
          }

          apply plugin: 'bar'
          ext.magic = 42

          android {
            whatever
          }

          dependencies {
            implementation 'heart:of-gold:1.0'
            api project(":marvin")
            api gradleApi()
            api localGroovy()
            api gradleKotlinDsl()

            testImplementation("pan-galactic:gargle-blaster:2.0-SNAPSHOT") {
              because "life's too short not to"
            }
          }

          println 'hello, world'
        '''.stripIndent()
    )

    when:
    def list = parseGroovyGradleScript(sourceFile)

    then:
    assertThat(list).containsExactly(
      'heart:of-gold:1.0',
      'project(":marvin")',
      'gradleApi()',
      'localGroovy()',
      'gradleKotlinDsl()',
      'pan-galactic:gargle-blaster:2.0-SNAPSHOT'
    )
  }

  def "can parse complex script"() {
    given:
    def sourceFile = dir.resolve('build.gradle')
    Files.writeString(
      sourceFile,
      '''\
          //file:noinspection GroovyAssignabilityCheck
          //file:noinspection GrUnresolvedAccess
          import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
          import org.gradle.internal.jvm.Jvm
          import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
          import proguard.gradle.ProGuardTask

          buildscript {
            repositories {
              maven {
                url 'some-url'
              }
            }
            dependencies {
              // There is no plugin for JVM proguard (yet)
              classpath 'com.guardsquare:proguard-gradle:7.1.0'
            }
          }

          plugins {
            id 'build-logic-base-convention'
            id 'publish-artifactory-convention'
            id 'org.jetbrains.kotlin.plugin.serialization'
            id 'application'
            id 'com.squareup.tools.generate'
            id 'com.github.johnrengelman.shadow'
          }

          group = 'com.foo.tools'
          version = '0.1.0'
          ext.projectName = 'foo-toolbox'

          application {
            setApplicationDefaultJvmArgs([
                '-XX:+IgnoreUnrecognizedVMOptions',
                '--add-modules=jdk.zipfs'
            ])
            mainClass.set('com.foo.bar.MainKt')
          }

          configurations {
            proguard
          }

          dependencies {
            implementation project(':aws')
            implementation platform(project(':platform'))
            implementation project(':xml-combiner')

            implementation(libs.commonsCompress) {
              because 'Better ZipFile implementation'
            }

            implementation(libs.jgit.core) {
              because 'Better than the command line git via ProcessBuilder.'
            }

            implementation(libs.jgit.ssh.core) {
              because 'Ssh factory for jgit.'
            }
            implementation(libs.jgit.ssh.agent) {
              because 'Ssh Agent for jgit.'
            }

            implementation libs.guava
            implementation libs.kotlin.serialization
            implementation libs.maven.artifact
            implementation libs.mdx.stdlib
            implementation libs.okhttp.core
            implementation libs.okhttp.logging.interceptor
            implementation libs.okio
            implementation libs.picocli.core
            implementation libs.progressbar
            implementation libs.redundent.xml
            implementation libs.sdklib.tools
            implementation libs.sentry
            implementation libs.slf4j.simple
            implementation libs.koin

            // Proguard needs these available for processing, but we don't need them at runtime.
            proguard libs.j2objc
            proguard libs.checker
            proguard libs.errorprone
            proguard libs.javax.annotation
            proguard(libs.proguard.javax.servlet) {
              because 'com.android.tools:sdklib'
            }
            proguard(libs.proguard.ibm.icu) {
              because 'com.android.tools:sdklib'
            }

            testImplementation(libs.jimfs) {
              because 'create the filesystem in memory'
            }
            testImplementation libs.okhttp.mockwebserver
            testImplementation libs.xmlunit
          }

          generate {
            sources {
              register('kotlin/com/foo/bar/Version.kt') {
                values = [version: version]
                template = """\
              @file:Suppress("PackageDirectoryMismatch") // false positive
              package com.foo.bar

              object Version {
                const val name = "sa-toolbox %version%"
              }
              """.stripIndent()
              }
            }
          }

          tasks.withType(KotlinCompile).configureEach {
            kotlinOptions {
              jvmTarget = JavaVersion.VERSION_11
              allWarningsAsErrors = false
            }
          }

          tasks.withType(Test).configureEach {
            useJUnitPlatform()
          }

          // Copy distribution files needed for tests into build/resources/test
          tasks.named('processTestResources', ProcessResources) {
            from 'src/dist'
          }

          /*
           * First we create a fat jar, and then we minify it, then finally we publish it.
           *
           * nb: This is largely copy-pasta from tools/projects/setup-ide-modules, and is therefore a good
           * candidate for turning into a plugin.
           */

          // => "bar-1.0"
          ext.baseCoordinates = "${projectName}-${project.version}"

          def shadowJar = tasks.named('shadowJar', ShadowJar) {
            doNotTrackState(
                """\
                The jar remains up to date even when changing the excludes.
                See also https://github.com/johnrengelman/shadow/issues/62#issuecomment-877728948
                """.stripIndent()
            )

            group = 'Build'
            description = 'Produces a fat jar'
            archiveFileName = "${baseCoordinates}-all.jar"
            reproducibleFileOrder = true

            from sourceSets.main.output
            from project.configurations.runtimeClasspath

            exclude 'META-INF/maven/**'
          }

          ext.packageFilters = [
              // org.jline.terminal is a dependency of 'me.tongfei:progressbar'. Jline does not appear to declare
              // any of its dependencies (per build scans). Filtering them means that Proguard doesn't attempt
              // any minification related to these packages.
              '!org/jline/terminal/impl/jna/**',
              '!org/jline/terminal/impl/jansi/**',
              '!org/jline/builtins/ssh/**',

              // JGit has a number of apparently runtime-only dependencies (org.apache.sshd:sshd-common:2.7.0).
              '!org/apache/sshd/agent/unix/**',
              '!org/apache/sshd/agent/local/ProxyAgentFactory.class',
              '!org/apache/sshd/agent/local/ProxyAgentFactory$1.class',
              '!org/apache/sshd/common/util/security/bouncycastle/**',
          ].join(',')

          def minify = tasks.register('minify', ProGuardTask) {
            // TODO: https://github.com/Guardsquare/proguard/issues/254
            notCompatibleWithConfigurationCache('Accesses Project at execution time')

            configuration file('proguard.pro')

            // injars: the classpath that must be processed
            injars(filter: packageFilters, shadowJar.flatMap { it.archiveFile })
            // libraryjars: on the classpath but not to be processed (i.e., the JDK)
            libraryjars(javaRuntime() + configurations.proguard)

            // the minified jar output
            outjars(layout.buildDirectory.file("libs/${baseCoordinates}-minified.jar"))
          }

          // This is a sanity check. You can execute `./gradlew runMin [--args="arg..."]` to verify that the
          // minified binary works.
          tasks.register('runMin', JavaExec) {
            classpath = files(minify)
          }

          tasks.withType(Test).configureEach {
            // print out results on the command line
            testLogging {
              events 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
            }
          }

          tasks.withType(JavaExec).configureEach {
            // set all the gradlew runs to debug.
            setAllJvmArgs(getAllJvmArgs() + ["-Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG"])
          }

          tasks.withType(CreateStartScripts).configureEach {
            applicationName = projectName
            // somehow, when setting the template, we lose the main class. Reset to make sure it's here.
            mainClass.set(application.mainClass)
            unixStartScriptGenerator.template =
                resources.text.fromFile('src/main/shell/sa-toolbox-startup.sh.template')
          }

          // Set our start script to have the minified binary as the classpath.
          def startShadowScripts = tasks.named('startShadowScripts', CreateStartScripts) {
            classpath = files(minify)
          }

          // Zip the relevant files (there should be only two) into a single coherent archive.
          def minifiedDistZip = tasks.register('minifiedDistZip', Zip) { zipTask ->
            def zipRoot = "/${baseCoordinates}"
            zipTask.archiveBaseName.set(projectName)
            zipTask.archiveClassifier.set('minified')

            // jar
            zipTask.from(minify) {
              into("$zipRoot/lib")
            }

            // bash script
            zipTask.from(startShadowScripts) {
              exclude {
                // Skipping Windows script because who cares
                it.name.endsWith('.bat')
              }
              into("$zipRoot/bin")
            }

            // raw files
            zipTask.from('src/dist') {
              into(zipRoot)
            }
          }

          // This will unzip minifiedDistZip into a new directory, `binary`, in this project. That directory
          // should be checked in with git lfs and then a symlink created in the register root pointing to the
          // bash script at binary/bin/<app name>.
          // nb: this unzips the previously created zip, but the two tasks are logically separate and each has
          // their own valid use-case, so we suffer the very tiny performance hit to zip and then immediately
          // unzip.
          tasks.register('buildBinary', Sync) {
            dependsOn minifiedDistZip

            from(zipTree(minifiedDistZip.flatMap { it.archiveFile })) {
              // drop the <app name>-<version>/ directory prefix
              eachFile { fcd ->
                fcd.relativePath = new RelativePath(true, fcd.relativePath.segments.drop(1))
              }
              includeEmptyDirs = false
            }
            into layout.projectDirectory.dir('binary')
          }

          // Not strictly necessary at this time, but a toehold if we ever decide to publish this artifact to
          // an external repo.
          publishing {
            publications {
              minifiedDistribution(MavenPublication) {
                artifactId = projectName
                artifact minifiedDistZip
              }
            }
          }

          /**
           * @return The JDK, for use by Proguard.
           */
          List<File> javaRuntime() {
            Jvm jvm = Jvm.current()
            FilenameFilter filter = { _, fileName -> fileName.endsWith(".jar") || fileName.endsWith(".jmod") }

            return ['jmods' /* JDK 9+ */, 'bundle/Classes' /* mac */, 'jre/lib' /* linux */]
                .collect { new File(jvm.javaHome, it) }
                .findAll { it.exists() }
                .collectMany { it.listFiles(filter) as List }
                .toSorted()
                .tap {
                  if (isEmpty()) {
                    throw new IllegalStateException("Could not find JDK ${jvm.javaVersion.majorVersion} runtime")
                  }
                }
          }
        '''.stripIndent()
    )

    when:
    def list = parseGroovyGradleScript(sourceFile)

    then:
    assertThat(list).containsExactly(
      'com.guardsquare:proguard-gradle:7.1.0',
      "project(':aws')",
      "project(':platform')",
      "project(':xml-combiner')",
      'libs.commonsCompress',
      'libs.jgit.core',
      'libs.jgit.ssh.core',
      'libs.jgit.ssh.agent',
      'libs.guava',
      'libs.kotlin.serialization',
      'libs.maven.artifact',
      'libs.mdx.stdlib',
      'libs.okhttp.core',
      'libs.okhttp.logging.interceptor',
      'libs.okio',
      'libs.picocli.core',
      'libs.progressbar',
      'libs.redundent.xml',
      'libs.sdklib.tools',
      'libs.sentry',
      'libs.slf4j.simple',
      'libs.koin',
      'libs.j2objc',
      'libs.checker',
      'libs.errorprone',
      'libs.javax.annotation',
      'libs.proguard.javax.servlet',
      'libs.proguard.ibm.icu',
      'libs.jimfs',
      'libs.okhttp.mockwebserver',
      'libs.xmlunit',
    )
  }

  private static parseGroovyGradleScript(Path file) {
    def input = Files.newInputStream(file, StandardOpenOption.READ).withCloseable {
      CharStreams.fromStream(it)
    }
    def lexer = new GradleGroovyScriptLexer(input)
    def tokens = new CommonTokenStream(lexer)
    def parser = new GradleGroovyScript(tokens)

    def tree = parser.script()
    def walker = new ParseTreeWalker()
    def listener = new GroovyGradleScriptListener(parser, input)
    walker.walk(listener, tree)

    return listener.list
  }

  private static class GroovyGradleScriptListener extends GradleGroovyScriptBaseListener {

    private final GradleGroovyScript parser
    private final CharStream input

    List list = new ArrayList()

    GroovyGradleScriptListener(GradleGroovyScript parser, CharStream input) {
      this.parser = parser
      this.input = input
    }

    @Override
    void enterScript(GradleGroovyScript.ScriptContext ctx) {
      println(ctx.text)
    }

    @Override
    void enterDependencies(GradleGroovyScript.DependenciesContext ctx) {
      println(ctx.text)
    }

    @Override
    void exitDependencies(GradleGroovyScript.DependenciesContext ctx) {
      println("exit dependencies")
    }

    @Override
    void enterNormalDeclaration(GradleGroovyScript.NormalDeclarationContext ctx) {
      def tokens = parser.tokenStream
      def configuration = tokens.getText(ctx.configuration())
      def dependency = tokens.getText(ctx.dependency())
      def closure = ctx.closure()?.with {
        tokens.getText(it)
      }
      println("tokens; conf=$configuration, dep=$dependency, closure=$closure")

      println(fullText(ctx))

      list += dependency
    }

    @Override
    void enterPlatformDeclaration(GradleGroovyScript.PlatformDeclarationContext ctx) {
      def tokens = parser.tokenStream
      def configuration = tokens.getText(ctx.configuration())
      def dependency = tokens.getText(ctx.dependency())
      def closure = ctx.closure()?.with {
        tokens.getText(it)
      }
      println("tokens; conf=$configuration, dep=$dependency, closure=$closure")

      println(fullText(ctx))

      list += dependency
    }

    @Override
    void enterTestFixturesDeclaration(GradleGroovyScript.TestFixturesDeclarationContext ctx) {
      def tokens = parser.tokenStream
      def configuration = tokens.getText(ctx.configuration())
      def dependency = tokens.getText(ctx.dependency())
      def closure = ctx.closure()?.with {
        tokens.getText(it)
      }
      println("tokens; conf=$configuration, dep=$dependency, closure=$closure")

      println(fullText(ctx))

      list += dependency
    }

//    @Override
//    void enterClosure(GradleGroovyScript.ClosureContext ctx) {
//      def tokens = parser.tokenStream
//      def text = tokens.getText(ctx)
//    }

    private String fullText(ParserRuleContext ctx) {
      def a = ctx.start.startIndex
      def b = ctx.stop.stopIndex
      def interval = new Interval(a, b)
      return input.getText(interval)
    }
  }
}
