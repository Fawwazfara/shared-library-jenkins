# Jenkins Shared Library — Multi-Stack CI

Shared library modular untuk CI/CD multi-stack: **Laravel/PHP**, **Node.js (NestJS/Express)**, dan **Go (Fiber)**.
Semua step dapat dikustomisasi lewat parameter tanpa mengubah kode library.

## Struktur

```
shared-library/
├── vars/                         # Global pipeline steps (dipanggil dari Jenkinsfile)
│   ├── checkoutCode.groovy       # Checkout dari Git SCM
│   ├── securityScan.groovy       # gitleaks + dependency audit (universal)
│   ├── dockerBuildPush.groovy    # Build & push image (Docker Hub / GHCR)
│   ├── buildLaravel.groovy       # composer install + pint + artisan test
│   ├── buildNodeJS.groovy        # npm ci + lint + test
│   ├── buildGo.groovy            # go mod download + golangci-lint + go test
│   └── appPipeline.groovy        # Wrapper pipeline pintar multi-stack
├── src/org/devops/               # Kelas logic reusable (Groovy)
│   ├── Config.groovy             # Default config terpusat (deep-merge)
│   ├── Utils.groovy              # Helper umum (tag, routing registry, dll)
│   ├── Builder.groovy            # Base class builder
│   ├── LaravelBuilder.groovy
│   ├── NodeJSBuilder.groovy
│   ├── GoBuilder.groovy
│   ├── BuilderFactory.groovy      # Factory + alias tipe (nestjs, fiber, php, ...)
│   ├── SecurityScanner.groovy
│   └── DockerBuilder.groovy
└── resources/org/devops/
    ├── defaults.yml               # Dokumentasi + default config
    ├── gitleaks.toml              # Config gitleaks siap pakai
    └── docker/
        ├── Dockerfile.laravel     # Template multi-stage
        ├── Dockerfile.nodejs
        └── Dockerfile.go
```

## Konfigurasi Library di Jenkins

**Manage Jenkins → System → Global Pipeline Libraries → Add**

| Field          | Nilai                                   |
|----------------|-----------------------------------------|
| Name           | `shared-library`                        |
| Default version| `main`                                  |
| Retrieval      | Modern SCM → Git                        |
| Project URL    | URL repo Git library ini                |
| Credentials    | credential yang punya akses (SSH/token) |

---

## Cara Pakai Cepat (Wrapper `appPipeline`)

Cara paling ringkas: satu baris memanggil seluruh pipeline
(checkout → build/test sesuai stack → security scan → docker build/push).

### Laravel

```groovy
@Library('shared-library@main') _

appPipeline(
    type: 'laravel',
    agent: 'docker',
    laravel: [ withNode: true, test: [ cmd: 'php artisan test --parallel' ] ],
    docker: [ registry: 'ghcr.io', credentialsId: 'ghcr-creds', imageName: 'my-org/laravel-app' ]
)
```

### NestJS / Express

```groovy
@Library('shared-library@main') _

appPipeline(
    type: 'nodejs',
    agent: 'docker',
    nodejs: [ nodeImage: 'node:22-alpine', packageManager: 'npm' ],
    docker: [ registry: 'docker.io', credentialsId: 'dockerhub-creds', imageName: 'myuser/nest-api' ]
)
```

### Go / Fiber

```groovy
@Library('shared-library@main') _

appPipeline(
    type: 'go',
    agent: 'docker',
    go: [ goImage: 'golang:1.23', build: [ enabled: true, output: 'bin/api' ] ],
    docker: [ registry: 'ghcr.io', credentialsId: 'ghcr-creds', imageName: 'my-org/fiber-api' ]
)
```

Opsi wrapper yang tersedia: `skipCheckout`, `skipSecurity`, `skipDocker`,
serta map per-override `checkout`, `security`, `docker`, dan `<stack>`
(`laravel` / `nodejs` / `go`). Bila `agent` disertakan, wrapper otomatis
membungkus eksekusi di dalam `node(agent)`.

---

## Cara Pakai Modular (step per tahap)

Jika ingin kontrol penuh/declarative pipeline:

### Laravel

```groovy
@Library('shared-library@main') _

pipeline {
    agent any
    stages {
        stage('CI') {
            steps {
                script {
                    checkoutCode(branch: 'main')
                    buildLaravel()
                    securityScan(type: 'laravel')
                    dockerBuildPush(registry: 'docker.io', imageName: 'myuser/laravel-app')
                }
            }
        }
    }
}
```

### NestJS / Express

```groovy
@Library('shared-library@main') _

pipeline {
    agent any
    stages {
        stage('CI') {
            steps {
                script {
                    checkoutCode(branch: 'develop')
                    buildNodeJS(packageManager: 'npm')
                    securityScan(type: 'nodejs')
                    dockerBuildPush(registry: 'ghcr.io',
                                    credentialsId: 'ghcr-creds',
                                    imageName: 'my-org/nest-api',
                                    additionalTags: ['latest'])
                }
            }
        }
    }
}
```

### Go / Fiber

```groovy
@Library('shared-library@main') _

pipeline {
    agent any
    stages {
        stage('CI') {
            steps {
                script {
                    checkoutCode(branch: 'main', credentialsId: 'github-ssh')
                    buildGo(lint: [ args: '--timeout=10m' ])
                    securityScan(type: 'go')
                    dockerBuildPush(registry: 'ghcr.io',
                                    credentialsId: 'ghcr-creds',
                                    imageName: 'my-org/fiber-api',
                                    buildx: true,
                                    platform: 'linux/amd64,linux/arm64')
                }
            }
        }
    }
}
```

---

## Referensi Step

### `checkoutCode(Map config)`
| Key | Default | Keterangan |
|-----|---------|------------|
| `url` | `''` | Kosong → pakai SCM bawaan job (`checkout scm`) |
| `branch` | `''` | Branch/tag target |
| `credentialsId` | `''` | Credential Git |
| `changelog` | `true` | Aktifkan changelog |
| `poll` | `false` | Aktifkan polling trigger |
| `sparseCheckout` | – | `List<String>` path (sparse checkout) |
| `timeout` | `15` | Menit |

### `buildLaravel(Map config)`
`composer install` → (opsional) frontend build → `pint` → (opsional) `phpstan` → `php artisan test`.
Kunci: `phpImage`, `composerImage`, `nodeImage`, `installArgs`, `withNode`, `lint`, `staticAnalysis`, `test`, `envFile`.

### `buildNodeJS(Map config)`
Install (auto-detect `npm ci`/`yarn`/`pnpm`) → lint → (opsional) build → test.
Kunci: `nodeImage`, `packageManager`, `installCmd`, `lint`, `build`, `test`.

### `buildGo(Map config)`
`go mod download` → `golangci-lint` → `go test` → (opsional) `go build`.
Kunci: `goImage`, `golangciImage`, `lint`, `test`, `build`.

### `securityScan(Map config)`
Menjalankan **gitleaks** (secret scan) + **dependency audit** sesuai `type`:
`composer audit` (laravel), `npm audit` (nodejs), `govulncheck` (go).
Kunci: `type`, `enabled`, `failOnFindings`, `gitleaks`, `dependencyAudit`.

### `dockerBuildPush(Map config)`
Build → (opsional) lint Dockerfile & image scan → login & push.
Registry **Docker Hub** dan **GHCR** didukung; provider dideteksi otomatis
dari hostname registry, atau set `registryProvider: 'ghcr' | 'dockerhub'`.
Kunci: `registry`, `registryProvider`, `credentialsId`, `imageName`,
`imageTag`, `additionalTags`, `dockerfile`, `context`, `buildArgs`,
`platform`, `buildx`, `push`, `scanImage`, `lintDockerfile`.

---

## Default Config

Semua default terpusat di `src/org/devops/Config.groovy` dan diduplikasi
sebagai dokumentasi di `resources/org/devops/defaults.yml`. Parameter yang
Anda kirim ke step akan di-**deep-merge** dengan default sehingga cukup
menimpa nilai yang perlu saja.

## Catatan

- Step `build*` dan `dockerBuildPush` menjalankan tooling di dalam container
  via `docker run ... -v $(pwd):/workspace`, sehingga agent hanya butuh Docker.
  Jika image tooling dikosongkan, perintah dijalankan langsung di host.
- Credential untuk `dockerBuildPush` memakai `usernamePassword` (variable
  `DOCKER_USERNAME` / `DOCKER_PASSWORD`). Untuk GHCR isi username = GitHub
  username, password = Personal Access Token dengan scope `write:packages`.
- Template Dockerfile per stack tersedia di `resources/org/devops/docker/`.
