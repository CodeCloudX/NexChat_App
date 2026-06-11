$dirs = Get-ChildItem -Path "C:\Users\Student\Documents\nexchat\NexChatApp" -Recurse -Filter "build.gradle.kts" | Select-Object -ExpandProperty DirectoryName
foreach ($dir in $dirs) {
    $path = Join-Path $dir "consumer-rules.pro"
    if (-not (Test-Path $path)) {
        New-Item -ItemType File -Path $path -Force
    }
}
