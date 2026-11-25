# flatmarkdown-java

Java bindings for [flatmarkdown](https://crates.io/crates/flatmarkdown) via Java FFM (Foreign Function & Memory) API.

The native library is bundled inside the JAR and loaded automatically at runtime.

## Requirements

- Java 25+
- Rust toolchain (for building the native library)

Maven is not required. The Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows) downloads Maven 3.9 on first use.
Use the wrapper instead of a system `mvn`. Maven 3 and Maven 4 deploy to Maven Central in different ways. The wrapper pins Maven 3 to match a local `settings.xml` written for Maven 3.

## API

```java
import com.sosuisha.flatmarkdown.FlatmarkdownJava;

// Split a Flat Markdown page into items with metadata
String page = FlatmarkdownJava.pageToAst("<!-- @@ 2026-09-27-15-00-16-W3N8ASY -->\n- [ ] Task1\n\nParagraph");
// => {"type":"page","front_matter":null,"items":[{"level":2,"id":"2026-09-27-15-00-16-W3N8ASY","properties":{},"body":"- [ ] Task1","ast":{...}},{"level":1,"id":null,...}]}

// Convert one item body
String html = FlatmarkdownJava.bodyToHtml("# Hello");
// => "<h1>Hello</h1>\n"

String ast = FlatmarkdownJava.bodyToAst("# Hello");
// => {"type":"document","children":[{"type":"heading","level":1,...}]}
```

`markdownToHtml` and `markdownToAst` still work but are deprecated. Use `bodyToHtml` and `bodyToAst` instead.

The AST format is described in the [flatmarkdown AST specification](https://github.com/sosuisen/flatmarkdown/blob/main/SPEC_AST.md).

## Build

```sh
# Build native library
cargo build --release

# Copy DLL to Maven resources
mkdir -p src/main/resources/win32-x86-64
# Win
cp target/release/flatmarkdown_java.dll src/main/resources/win32-x86-64/
# macOS
cp target/release/libflatmarkdown_java.dylib src/main/resources/darwin-aarch64/
# Linux
cp target/release/libflatmarkdown_java.so src/main/resources/linux-x86-64/
```

Or simply run `bash build.sh`.

## Maven dependency

```xml
<dependency>
    <groupId>com.sosuisha</groupId>
    <artifactId>flatmarkdown</artifactId>
    <version>1.0.1</version>
</dependency>
```

The consuming application must pass `--enable-native-access=ALL-UNNAMED` as a JVM argument.

## Release

Releases are published to Maven Central by GitHub Actions (`.github/workflows/release.yml`).

The workflow runs when a tag that starts with `v` is pushed, for example `v1.0.0`.
It builds the native library on macOS (arm64), Windows (x86-64), and Linux (x86-64), bundles all three into one JAR, signs the files with GPG, and runs `./mvnw deploy`.
The Linux library is built with `cargo-zigbuild` against glibc 2.17, so it runs on most distributions released since 2014.

Before the first release, add these four **Repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `GPG_PRIVATE_KEY` | Output of `gpg --armor --export-secret-keys <KEY_ID>` |
| `GPG_PASSPHRASE` | Passphrase of the GPG key |
| `CENTRAL_USERNAME` | Username part of the Central Portal user token |
| `CENTRAL_PASSWORD` | Password part of the Central Portal user token |

The public key must be on a key server, or Maven Central cannot verify the signatures:

```sh
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

To release:

1. Set a non-SNAPSHOT version in `pom.xml` and commit it.
2. Tag and push:

```sh
git tag v1.0.0
git push origin v1.0.0
```
