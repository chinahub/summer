# 发布到 Maven Central

> 本文档记录 summer 框架发布到 Maven Central（Sonatype Central Portal）的完整流程，以及发布过程中遇到的典型问题与解决方案。

## 发布机制概述

发布通过 `release` profile 激活，该 profile 绑定了以下插件：

- **maven-source-plugin**：打包源码 jar
- **maven-javadoc-plugin**：打包 javadoc jar
- **maven-gpg-plugin**：对每个构件（pom / jar / sources / javadoc）做 GPG 签名
- **central-publishing-maven-plugin**（`<extensions>true</extensions>`）：替换默认 `maven-deploy-plugin`，将所有构件上传到 Sonatype Central Portal 的 staging 区

`release` profile 只在发布时用 `-Prelease` 激活；日常本地构建不激活，跳过签名与上传，避免误触发。

```xml
<profile>
    <id>release</id>
    <build>
        <plugins>
            <!-- ...source / javadoc / gpg / central-publishing... -->
        </plugins>
    </build>
</profile>
```

## 前置条件

### 1. JDK 与 Maven

- JDK 25+（实测 jdk-25.0.2）
- Maven 3.9+（实测 mvnd 1.0.5 内置 Maven 3.9.14）

### 2. GPG 签名密钥

Central 要求每个构件都用 GPG 签名，且公钥必须上传到公钥服务器可查。

```bash
# 生成 4096 位 RSA 密钥
gpg --full-generate-key

# 查看密钥 ID
gpg --list-secret-keys --keyid-format LONG

# 上传公钥到公钥服务器（两个都发）
gpg --send-keys <KEYID> --keyserver keys.openpgp.org
gpg --send-keys <KEYID> --keyserver keyserver.ubuntu.com
```

pom.xml 中通过 `<keyname>` 指定签名用的密钥 UID：

```xml
<configuration>
    <keyname>cn.jiebaba.summer</keyname>
</configuration>
```

### 3. Sonatype Central Portal 凭据

在 [Central Portal](https://central.sonatype.com) 注册账号后，生成 **Access User Token**（不是账号密码）。将 token 的 name / password 配置到 Maven 的 `settings.xml` 中：

```xml
<servers>
    <server>
        <id>ossrh</id>
        <username>你的_token_name</username>
        <password>你的_token_secret</password>
    </server>
</servers>
```

pom.xml 中 `central-publishing-maven-plugin` 的 `<publishingServerId>ossrh</publishingServerId>` 即对应此 server id。

> ⚠️ 项目的 `settings.xml`（被 git 跟踪）只有镜像配置，不含凭据，不能放敏感信息。凭据应放在 **全局** `~/.m2/settings.xml`（不入库）。

## 发布流程

### 一键发布

项目根目录的 `build.ps1` 封装了发布命令。`deploy` 或 `-Prelease` 会自动路由到 Git Bash 执行（原因见下文「GPG 签名问题」）：

```powershell
# 等价于：mvn -Prelease deploy
.\build.ps1 deploy

# 也可显式传参
.\build.ps1 clean deploy -Prelease
```

执行后：
1. 全模块编译 → 生成 jar / sources / javadoc
2. GPG 对每个构件签名
3. `central-publishing-maven-plugin` 将所有构件暂存（staging）→ 打包成 `central-bundle.zip` → 上传到 Central Portal
4. 输出 deploymentId，提示去 Portal 手动发布

### 发布完成后

`central-publishing-maven-plugin` 默认 **不会自动发布**（pom 未配 `<autoPublish>true</autoPublish>`）。上传后构件处于 staging 状态，需到 [Central Portal 发布页](https://central.sonatype.com/publishing/deployments) 手动点击 **Publish** 完成。

> 如需自动发布，可在插件配置中添加：
> ```xml
> <configuration>
>     <publishingServerId>ossrh</publishingServerId>
>     <autoPublish>true</autoPublish>
> </configuration>
> ```

## 典型问题与解决方案

### 问题一：GPG 签名失败 — No Keybox daemon running

**现象**：`maven-gpg-plugin` 报错：

```
gpg: error running '/usr/lib/gnupg/keyboxd': probably not installed
gpg: failed to start keyboxd '/usr/lib/gnupg/keyboxd': Configuration error
gpg: can't connect to the keyboxd: Configuration error
gpg: error opening key DB: No Keybox daemon running
```

**根因**：pom.xml 中 `<executable>D:\Git\usr\bin\gpg.exe</executable>` 指定的是 Git 自带的 MSYS2 版 GnuPG 2.4.x。它的 `keyboxd` 守护进程路径编译为 `/usr/lib/gnupg/keyboxd`，这个路径**只有 MSYS2 运行时挂载表才能解析**到 `D:\Git\usr\lib\gnupg\keyboxd.exe`。当 Maven（java.exe，Windows 原生进程）直接 spawn `gpg.exe` 时，MSYS2 托管未就绪 → keyboxd 找不到/起不来 → 报错。

**解决方案**：让整个 `mvn deploy` 在 **Git Bash**（MSYS2 环境）内运行，这样 gpg-agent / keyboxd 被 MSYS2 正确托管，Maven 派生的 gpg 即可连上守护进程。

`build.ps1` 已实现此逻辑——检测到 `deploy` 或 `-Prelease` 参数时，通过 `D:\Git\bin\bash.exe -c "..."` 启动 mvn：

```powershell
if ($mvnArgs -contains 'deploy' -or $mvnArgs -contains '-Prelease') {
    # ... 自动补 -Prelease，经 Git Bash 执行
    & $bash -c $cmd
} else {
    # 普通构建走 PowerShell
    & mvn -s E:\summer_workspace\settings.xml @mvnArgs
}
```

### 问题二：central-publishing 报 server is null

**现象**：`central-publishing-maven-plugin` 报错：

```
Unable to get publisher server properties for server id: ossrh:
Cannot invoke "Server.clone()" because "server" is null
```

**根因**：`central-publishing-maven-plugin` 按 `<publishingServerId>ossrh</publishingServerId>` 在 settings.xml 的 `<servers>` 中查找凭据。项目的 `settings.xml` 没有 `<servers>`（且被 git 跟踪，不应放凭据）。

**解决方案**：`build.ps1` 在 deploy 分支使用**全局** `~/.m2/settings.xml`（含 ossrh token + 镜像）而非项目 settings.xml：

```powershell
$globalSettings = Join-Path $env:USERPROFILE '.m2\settings.xml'
# mvn -s '$globalSettings' ...
```

### 问题三：测试模块被发布到 Central

**现象**：`build-test` 模块（本地测试框架）的构件也被暂存并上传到了 Maven Central。

**根因**：`build-test/pom.xml` 中加的 `<maven.deploy.skip>true</maven.deploy.skip>` **只对默认的 `maven-deploy-plugin` 生效**。但 `release` profile 里的 `central-publishing-maven-plugin` 设了 `<extensions>true</extensions>`，它**替换**了默认 deploy——整个 deploy 阶段跑的是 `central-publishing:publish`，不读 `maven.deploy.skip`。

该插件的反编译过滤逻辑显示，它按 **artifactId** 匹配 `excludeArtifacts` 列表来决定是否将构件纳入最终 bundle：

```java
// lambda$processRelease$1 过滤逻辑
if (excludeArtifacts.contains(artifact.getArtifactId())) return false; // 排除
```

**解决方案**：在父 pom 的 `central-publishing-maven-plugin` 配置中添加 `excludeArtifacts`，按 artifactId 排除 `build-test`：

```xml
<configuration>
    <publishingServerId>ossrh</publishingServerId>
    <!-- build-test 是本地测试框架，不发布到 Maven Central -->
    <excludeArtifacts>
        <exclude>build-test</exclude>
    </excludeArtifacts>
</configuration>
```

此配置在父 pom 的 `release` profile 中，被所有子模块继承。`build-test` 仍会正常编译、安装、运行测试，但其构件在最终打包时被过滤，不进入 `central-bundle.zip`，不影响其他模块上传。

> `build-test` 的 `<maven.deploy.skip>true</maven.deploy.skip>` 保留——对不激活 release profile 的默认 deploy 仍有意义。

## 构建脚本

`build.ps1` 支持以下用法：

```powershell
.\build.ps1                       # 默认 clean package（走 PowerShell）
.\build.ps1 clean compile         # 指定目标（走 PowerShell）
.\build.ps1 deploy                # 发布（自动补 -Prelease，经 Git Bash 执行）
.\build.ps1 package -Prelease     # 只测 GPG 签名不发布（也走 Git Bash）
```

deploy 路由到 Git Bash 的原因见上文「问题一」。普通构建（不含 deploy / -Prelease）不碰 GPG，走 PowerShell 即可。