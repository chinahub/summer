# summer 构建脚本
# 用法: .\build.ps1                       (默认 clean package)
#       .\build.ps1 clean compile
#       .\build.ps1 deploy                (deploy 自动经 Git Bash 执行, 让 Git 自带 gpg 在 MSYS2 环境运行)
#       .\build.ps1 clean deploy -Prelease

$env:JAVA_HOME = 'D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;D:\mvnd-1.0.5\mvn\bin;" + $env:Path

Write-Host "JAVA_HOME=$env:JAVA_HOME"

if ($args.Count -gt 0) {
    $mvnArgs = @($args)
} else {
    $mvnArgs = @('clean', 'package')
}

# deploy 必须在 Git Bash (MSYS2) 环境里运行: Git 自带的 gpg.exe 只有在 MSYS2 托管下才能正确启动
# keyboxd / gpg-agent 守护进程, 否则会报 "No Keybox daemon running" / "Configuration error"。
if ($mvnArgs -contains 'deploy' -or $mvnArgs -contains '-Prelease') {
    if ($mvnArgs -notcontains '-Prelease') {
        $mvnArgs = @('-Prelease') + $mvnArgs
    }
    $bash = 'D:\Git\bin\bash.exe'
    # 每个参数用单引号包裹, 避免被 bash 重新分词
    $argStr = ($mvnArgs | ForEach-Object { "'$_'" }) -join ' '
    $globalSettings = Join-Path $env:USERPROFILE '.m2\settings.xml'
    if (-not (Test-Path $globalSettings)) { throw "未找到全局 settings.xml: $globalSettings (需含 ossrh 凭据)" }
    # deploy 用全局 ~/.m2/settings.xml: 它含 ossrh 服务器凭据 + huaweicloud 镜像;
    # 项目的 settings.xml 没有 <servers>, 会致 central-publishing 报 "server is null"
    $cmd = "cd /e/summer_workspace && export JAVA_HOME=/d/jdk/jdk-25.0.2 && export PATH=`"/usr/bin:`$JAVA_HOME/bin:/d/mvnd-1.0.5/mvn/bin:`$PATH`" && mvn -s '$globalSettings' -f pom.xml $argStr"
    Write-Host "[deploy] running via Git Bash: $bash"
    Write-Host "[deploy] $cmd"
    & $bash -c $cmd
    exit $LASTEXITCODE
} else {
    & mvn -s E:\summer_workspace\settings.xml @mvnArgs
    exit $LASTEXITCODE
}