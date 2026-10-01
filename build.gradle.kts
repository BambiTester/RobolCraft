plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val releaseJarName = "robolcraft-${providers.gradleProperty("modVersion").get()}.jar"

val clearReleaseJars = tasks.register<Delete>("clearReleaseJars") {
    delete(
        fileTree(layout.projectDirectory.dir("release")) {
            include("*.jar")
        },
    )
}

tasks.register<Copy>("syncReleaseJar") {
    group = "distribution"
    description = "Replace release/*.jar with the plain jar from this build."
    dependsOn(tasks.named("build"), clearReleaseJars)
    from(layout.buildDirectory.file("libs/$releaseJarName"))
    into(layout.projectDirectory.dir("release"))
}
