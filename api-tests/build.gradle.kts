plugins { java }

repositories { mavenCentral() }

val generator = configurations.create("generator")
val spec = rootProject.layout.projectDirectory.file("src/main/resources/static/openapi.yaml")
val client = layout.buildDirectory.dir("generated/rewards-client")

dependencies {
    generator("org.openapitools:openapi-generator-cli:7.27.0")
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    implementation("io.rest-assured:rest-assured:5.5.7")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.20.1"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

val generateClient = tasks.register<JavaExec>("generateClient") {
    group = "openapi"
    description = "Generate the Rest Assured and Jackson client from the service contract"
    inputs.file(spec)
    outputs.dir(client)
    classpath = generator
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args("generate", "-g", "java", "--library", "rest-assured",
        "-i", spec.asFile.absolutePath, "-o", client.get().asFile.absolutePath,
        "--api-package", "org.example.rewards.client.api",
        "--model-package", "org.example.rewards.client.model",
        "--invoker-package", "org.example.rewards.client",
        "--additional-properties", "serializationLibrary=jackson,useJakartaEe=true,openApiNullable=false,hideGenerationTimestamp=true",
        "--global-property", "apiTests=false,modelTests=false,apiDocs=false,modelDocs=false")
}

sourceSets.main { java.srcDir(client.map { it.dir("src/main/java") }) }
tasks.compileJava { dependsOn(generateClient) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
tasks.test { useJUnitPlatform() }
