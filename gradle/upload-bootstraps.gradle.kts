import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileInputStream
import java.util.Base64

/**
 * Gradle task to upload bootstrap binaries to GitHub Releases.
 * 
 * Usage:
 *   ./gradlew uploadBootstrapsToGitHub -PgithubToken=your_token -PreleaseTag=v1.0.0
 * 
 * Or set GITHUB_TOKEN environment variable:
 *   export GITHUB_TOKEN=your_token
 *   ./gradlew uploadBootstrapsToGitHub -PreleaseTag=v1.0.0
 * 
 * Configuration (in gradle.properties):
 *   github.repo.owner=RiteshF7
 *   github.repo.name=termux-packages
 *   github.release.tag=v1.0.0
 */

tasks.register("uploadBootstrapsToGitHub") {
    group = "publishing"
    description = "Upload bootstrap ZIP files to GitHub Releases"

    doLast {
        val repoOwner = project.findProperty("github.repo.owner") as String? ?: "RiteshF7"
        val repoName = project.findProperty("github.repo.name") as String? ?: "termux-packages"
        val releaseTag = project.findProperty("github.release.tag") as String? 
            ?: project.findProperty("releaseTag") as String?
            ?: throw GradleException("Release tag required. Use -PreleaseTag=v1.0.0")
        
        // Try multiple sources for GitHub token (in order of preference):
        // 1. Environment variable
        // 2. Command line property
        // 3. local.properties file (gitignored)
        // 4. github.token file (gitignored)
        val githubToken = System.getenv("GITHUB_TOKEN") 
            ?: project.findProperty("githubToken") as String?
            ?: readTokenFromLocalProperties()
            ?: readTokenFromFile(File(project.rootDir, "github.token"))
            ?: throw GradleException("""
                GitHub token required. Use one of these methods:
                1. Set GITHUB_TOKEN environment variable
                2. Use -PgithubToken=your_token
                3. Add 'github.token=your_token' to local.properties
                4. Create github.token file in project root with your token
            """.trimIndent())
        
        val bootstrapsDir = File(project.rootDir.parentFile, "termux-packages/bootstraps")
        if (!bootstrapsDir.exists()) {
            throw GradleException("Bootstrap directory not found: ${bootstrapsDir.absolutePath}")
        }
        
        val bootstrapFiles = listOf(
            "bootstrap-aarch64.zip",
            "bootstrap-arm.zip",
            "bootstrap-i686.zip",
            "bootstrap-x86_64.zip"
        ).map { File(bootstrapsDir, it) }
        
        // Verify all files exist
        bootstrapFiles.forEach { file ->
            if (!file.exists()) {
                throw GradleException("Bootstrap file not found: ${file.absolutePath}")
            }
            println("Found: ${file.name} (${file.length() / 1024 / 1024} MB)")
        }
        
        println("\n=== Uploading bootstraps to GitHub Releases ===")
        println("Repository: $repoOwner/$repoName")
        println("Release Tag: $releaseTag")
        println("Files to upload: ${bootstrapFiles.size}")
        
        // Step 1: Create or get release
        val releaseId = createOrGetRelease(repoOwner, repoName, releaseTag, githubToken)
        println("Release ID: $releaseId")
        
        // Step 2: Upload each bootstrap file
        bootstrapFiles.forEach { file ->
            println("\nUploading ${file.name}...")
            uploadReleaseAsset(repoOwner, repoName, releaseId, file, githubToken)
            println("✓ Uploaded ${file.name}")
        }
        
        println("\n=== Upload Complete ===")
        println("Release URL: https://github.com/$repoOwner/$repoName/releases/tag/$releaseTag")
    }
}

fun createOrGetRelease(owner: String, repo: String, tag: String, token: String): String {
    val apiUrl = "https://api.github.com/repos/$owner/$repo/releases"
    
    // Try to get existing release first
    val getUrl = "$apiUrl/tags/$tag"
    val getConn = URL(getUrl).openConnection() as HttpURLConnection
    getConn.requestMethod = "GET"
    getConn.setRequestProperty("Authorization", "token $token")
    getConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
    
    if (getConn.responseCode == 200) {
        val response = getConn.inputStream.bufferedReader().use { it.readText() }
        val releaseId = extractJsonField(response, "id")
        println("Found existing release: $tag")
        return releaseId
    }
    
    // Create new release
    println("Creating new release: $tag")
    val createConn = URL(apiUrl).openConnection() as HttpURLConnection
    createConn.requestMethod = "POST"
    createConn.setRequestProperty("Authorization", "token $token")
    createConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
    createConn.setRequestProperty("Content-Type", "application/json")
    createConn.doOutput = true
    
    val releaseData = """
        {
            "tag_name": "$tag",
            "name": "Bootstrap Archives $tag",
            "body": "Bootstrap archives for Termux (aarch64, arm, i686, x86_64)",
            "draft": false,
            "prerelease": false
        }
    """.trimIndent()
    
    createConn.outputStream.use { it.write(releaseData.toByteArray()) }
    
    val responseCode = createConn.responseCode
    val response = if (responseCode in 200..299) {
        createConn.inputStream.bufferedReader().use { it.readText() }
    } else {
        val error = createConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
        throw GradleException("Failed to create release (HTTP $responseCode): $error")
    }
    
    return extractJsonField(response, "id")
}

fun uploadReleaseAsset(owner: String, repo: String, releaseId: String, file: File, token: String) {
    val uploadUrl = "https://uploads.github.com/repos/$owner/$repo/releases/$releaseId/assets?name=${file.name}"
    
    val conn = URL(uploadUrl).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Authorization", "token $token")
    conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
    conn.setRequestProperty("Content-Type", "application/zip")
    conn.doOutput = true
    conn.setFixedLengthStreamingMode(file.length().toInt())
    
    FileInputStream(file).use { input ->
        conn.outputStream.use { output ->
            input.copyTo(output)
        }
    }
    
    val responseCode = conn.responseCode
    if (responseCode !in 200..299) {
        val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
        throw GradleException("Failed to upload ${file.name} (HTTP $responseCode): $error")
    }
}

fun readTokenFromLocalProperties(): String? {
    val localPropsFile = File(project.rootDir, "local.properties")
    if (localPropsFile.exists()) {
        val props = java.util.Properties()
        localPropsFile.inputStream().use { props.load(it) }
        return props.getProperty("github.token")?.takeIf { it.isNotBlank() }
    }
    return null
}

fun readTokenFromFile(file: File): String? {
    if (file.exists()) {
        return file.readText().trim().takeIf { it.isNotBlank() }
    }
    return null
}

fun extractJsonField(json: String, fieldName: String): String {
    // Try to extract string value
    val stringPattern = "\"$fieldName\"\\s*:\\s*\"([^\"]+)\""
    val stringMatch = Regex(stringPattern).find(json)
    if (stringMatch != null) {
        return stringMatch.groupValues[1]
    }
    
    // Try to extract numeric value
    val numberPattern = "\"$fieldName\"\\s*:\\s*([0-9]+)"
    val numberMatch = Regex(numberPattern).find(json)
    if (numberMatch != null) {
        return numberMatch.groupValues[1]
    }
    
    throw GradleException("Could not extract $fieldName from JSON response")
}

