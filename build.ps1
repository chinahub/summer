# summer 构建脚本
# 用法: .\build.ps1              (默认 clean package)
#       .\build.ps1 clean compile

$env:JAVA_HOME = 'D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;D:\mvnd-1.0.5\mvn\bin;" + $env:Path

Write-Host "JAVA_HOME=$env:JAVA_HOME"

if ($args.Count -gt 0) {
    & mvn -s E:\summer_workspace\settings.xml @args
} else {
    & mvn -s E:\summer_workspace\settings.xml clean package
}
