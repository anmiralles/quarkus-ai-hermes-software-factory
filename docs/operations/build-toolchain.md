# Build toolchain on the factory box (JDK + Maven)

Card: **t_e1bded4b**. Provisioned on 2026-09-27.

## Why this exists

The three Coffee implementation/verification cards (`t_f9e0a903`, `t_8046549a`,
`t_c41f5e3e`) all accept on `./mvnw test` / `./mvnw clean verify`. On the factory box
none of that could run:

| probe | result at the time |
| --- | --- |
| `java -version` | command not found, `/usr/lib/jvm` absent |
| `mvn -v` | command not found |
| `docker info` | daemon unreachable → no container fallback |
| `apt-get` install | needs root; sessions run as uid 10000 |
| `curl https://repo.maven.apache.org/maven2/` | HTTP 200 (network was fine) |

So the toolchain is installed **user-level** under `/opt/data/toolchains` — writable by
uid 10000 and on the persistent volume — instead of via the package manager.

## What is installed

| | |
| --- | --- |
| JDK | Eclipse Temurin **21.0.12.1+1** (LTS, `21.0.12+101.0.LTS`) |
| JDK path | `/opt/data/toolchains/jdk-21.0.12.1+1` (symlink `jdk-21`) |
| JDK source | `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz` |
| JDK sha256 | `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94` (verified) |
| Maven | Apache Maven **3.9.16** |
| Maven path | `/opt/data/toolchains/apache-maven-3.9.16` (symlink `maven`) |
| Maven source | `https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz` |
| Maven sha512 | `831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6` (matched the published `.sha512`) |

Both archives are kept in `/opt/data/toolchains/_dl/` so re-provisioning needs no
re-download. `scripts/provision-toolchain.sh` reproduces the whole install and is
idempotent.

## How a bot session sees it

`/opt/data/toolchains/env.sh` exports `JAVA_HOME`, `M2_HOME` and prepends
`$JAVA_HOME/bin:$M2_HOME/bin` to `PATH`. It is sourced from `~/.profile` and `~/.bashrc`
of each factory bot profile (`devops`, `backend-developer`, `qa-tester`, `architect`,
`infosec`, `frontend-developer`).

That is the file that actually matters: the Hermes local terminal backend builds a
**session env snapshot** from a login shell that auto-sources `~/.profile`, `~/.bash_profile`
and `~/.bashrc` (`terminal.auto_source_bashrc`, default true), and every command in a
session then runs in a **non-login** `bash -c` with that snapshot environment. So a bot
session needs no manual `source`, and `java` is on `PATH` for plain commands.

Two consequences worth knowing:

- A session that was **already running** when the toolchain was installed keeps its old
  snapshot. It works after a new session, or immediately with
  `. /opt/data/toolchains/env.sh`.
- The wrapper and Maven resolve the JDK from `JAVA_HOME`; nothing depends on a distro
  JDK being present.

Maven's local repository lives at **`/opt/data/.m2/repository`**, not under the profile
home: the JVM derives `user.home` from the passwd entry for uid 10000 (`/opt/data`), not
from `$HOME`. That is a shared cache across all bot profiles — good for build time, and
worth remembering when debugging "dependency not found" (check `/opt/data/.m2`).

## Verification (raw output)

Fresh **non-login** shell inside a **new** Hermes session for profile `devops`, running
`/opt/data/toolchains/verify-toolchain.sh`:

```console
uid=10000(hermes) gid=10000(hermes) groups=10000(hermes)
HOME=/opt/data/profiles/devops/home
PATH=/opt/data/toolchains/jdk-21.0.12.1+1/bin:/opt/data/toolchains/apache-maven-3.9.16/bin:/usr/local/bin:/usr/bin:/bin:/usr/local/games:/usr/games
JAVA_HOME=/opt/data/toolchains/jdk-21.0.12.1+1
openjdk version "21.0.12.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS, mixed mode, sharing)
javac 21.0.12.1
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: /opt/data/toolchains/apache-maven-3.9.16
Java version: 21.0.12.1, vendor: Eclipse Adoptium, runtime: /opt/data/toolchains/jdk-21.0.12.1+1
```

Throwaway Maven project with a wrapper (`mvn -N wrapper:wrapper -Dmaven=3.9.16`, so
`distributionType=only-script` and no wrapper jar is committed):

```console
$ ./mvnw -v
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: /opt/data/profiles/devops/home/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f
Java version: 21.0.12.1, vendor: Eclipse Adoptium, runtime: /opt/data/toolchains/jdk-21.0.12.1+1
```

```console
$ ./mvnw -B clean package
[INFO] Downloaded from central: https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.jar (703 kB at 3.4 MB/s)
[INFO] Building jar: .../target/mvn-probe-1.0.0.jar
[INFO] BUILD SUCCESS
```

The build compiles a class that calls `commons-lang3`, so the dependency resolution is
exercised through the JVM, not just through `curl`.

## Operating notes

- **Blast radius of a change here:** environment only. Adding a toolchain to `PATH` does
  not touch any repository build file, and no service restarts. A bad `env.sh` would
  affect every bot session, so keep the script's self-check green before wiring it.
- **Rollback:** delete `/opt/data/toolchains` and the two-line `env.sh` stanza in each
  profile's `~/.profile` / `~/.bashrc`. Sessions started after that fall back to the
  original (JDK-less) state. Takes effect for new sessions only.
- **Not baked into the image.** This is a host-level install on the `/opt/data` volume.
  A deployment that recreates the container without that volume must re-run
  `scripts/provision-toolchain.sh`. That is the reason the install lives in this repo as
  a script rather than only in shell history.
- **No credentials** are involved: both artifacts are public and fetched over HTTPS with
  checksum verification.
