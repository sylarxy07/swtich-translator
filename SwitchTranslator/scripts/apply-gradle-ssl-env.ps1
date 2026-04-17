# Dot-source before running Gradle: . .\scripts\apply-gradle-ssl-env.ps1
# Uses Windows certificate store (Java 11+). Helps PKIX / SSL handshake failures.
$opts = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
$env:JAVA_TOOL_OPTIONS = "$opts $env:JAVA_TOOL_OPTIONS".Trim()
$env:JAVA_OPTS = "$opts $env:JAVA_OPTS".Trim()
$env:GRADLE_OPTS = "$opts $env:GRADLE_OPTS".Trim()
Write-Host "JAVA_TOOL_OPTIONS / JAVA_OPTS / GRADLE_OPTS include: $opts"
