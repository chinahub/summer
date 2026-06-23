# summer test script
# usage: .\test.ps1

$env:JAVA_HOME = 'D:\jdk\jdk-25.0.2'
$env:Path = "D:\jdk\jdk-25.0.2\bin;" + $env:Path

$root = Resolve-Path "$PSScriptRoot\.."

$pg = "C:\Users\CodexSandboxOffline\.m2\repository\org\postgresql\postgresql\42.7.7\postgresql-42.7.7.jar"
$cp = @(
    "$root\summer-core\target\summer-core-1.0.0-SNAPSHOT.jar",
    "$root\summer-web\target\summer-web-1.0.0-SNAPSHOT.jar",
    "$root\summer-data\target\summer-data-1.0.0-SNAPSHOT.jar",
    "$root\summer-boot\target\summer-boot-1.0.0-SNAPSHOT.jar",
    "$root\summer-sample\target\summer-sample-1.0.0-SNAPSHOT.jar",
    $pg
) -join ';'

$java = "D:\jdk\jdk-25.0.2\bin\java.exe"

function Run-Test($name, $className, $extraArgs) {
    Write-Host "=== $name ===" -ForegroundColor Cyan
    $args = @("-cp", $cp)
    if ($extraArgs) { $args += $extraArgs }
    $args += $className
    $output = & $java @args 2>&1 | ForEach-Object {
        if ($_ -is [System.Management.Automation.RemoteException]) { $_.ToString() } else { $_ }
    }
    $passed = $output | Where-Object { $_ -match "assertions passed" }
    $failed = $output | Where-Object { $_ -match "FAIL|Exception|Error" -and $_ -notmatch "caught \(expected\)" }
    if ($passed) {
        Write-Host $passed -ForegroundColor Green
    } elseif ($failed) {
        Write-Host "FAILED:" -ForegroundColor Red
        $failed | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    } else {
        Write-Host "NO RESULT (check classpath/class name)" -ForegroundColor Yellow
        $output | Select-Object -First 5 | ForEach-Object { Write-Host "  $_" }
    }
    Write-Host ""
}

Run-Test "OrmSmokeTest (pure logic)" "cn.jiebaba.summer.sample.OrmSmokeTest"
Run-Test "MultiDsSmokeTest (pure logic)" "cn.jiebaba.summer.sample.MultiDsSmokeTest"
Run-Test "DbSmokeTest (real PostgreSQL)" "cn.jiebaba.summer.sample.DbSmokeTest"
Run-Test "WebSocketSmokeTest" "cn.jiebaba.summer.sample.WebSocketSmokeTest" @("-Dsummer.datasource.url=")
Run-Test "KeepAliveSmokeTest" "cn.jiebaba.summer.sample.KeepAliveSmokeTest" @("-Dsummer.datasource.url=")