# IBM Dependency Based Build (DBB) — Guia de Configuração e Execução

> **Repositório:** `zopeneditor-sample-wazi`  
> **Idioma:** Português do Brasil  
> **Versão DBB suportada:** 2.x / 3.x (zAppBuild) · 3.x (zBuilder)

---

## Sumário

1. [Visão Geral](#1-visão-geral)
2. [Pré-requisitos](#2-pré-requisitos)
3. [Estrutura do Repositório](#3-estrutura-do-repositório)
4. [Configuração Inicial](#4-configuração-inicial)
5. [Configuração dos Datasets no z/OS](#5-configuração-dos-datasets-no-zos)
6. [Execução do Build Completo](#6-execução-do-build-completo)
7. [Execução por Tipo de Linguagem](#7-execução-por-tipo-de-linguagem)
8. [User Build (build de arquivo único)](#8-user-build-build-de-arquivo-único)
9. [Validação da Integração](#9-validação-da-integração)
10. [Solução de Problemas Comuns](#10-solução-de-problemas-comuns)
11. [Referência de Propriedades](#11-referência-de-propriedades)

---

## 1. Visão Geral

O **IBM Dependency Based Build (DBB)** compila e linka programas mainframe (COBOL, PL/I, Assembler, REXX) a partir de um repositório Git no z/OS UNIX System Services (USS), gerenciando dependências entre módulos automaticamente.

Este repositório contém:

| Diretório | Linguagem | Programas |
|-----------|-----------|-----------|
| `COBOL/`  | Enterprise COBOL | SAM1, SAM2, SAM1LIB, PCFDEMO |
| `PLI/`    | Enterprise PL/I  | PSAM1, PSAM2, PSAM1LIB, MACSAMP |
| `ASM/`    | HLASM Assembler  | ASAM1, MASAM1 |
| `REXX/`   | REXX             | RSAM1, FIB |
| `COPYBOOK/` | Copybooks COBOL | CUSTCOPY, TRANREC |
| `INCLUDES/` | Includes PL/I | CUSTPLI, BALSTATS |
| `ASMCOPY/` | Copybooks ASM | REGISTRS |

---

## 2. Pré-requisitos

### No ambiente local (workstation / Mac / Linux)

| Ferramenta | Versão mínima | Como verificar |
|-----------|---------------|----------------|
| **Zowe CLI** | 7.x | `zowe --version` |
| **Zowe CLI RSE API plug-in** | — | `zowe plugins list` |
| **Java** | 11+ | `java -version` |
| **IBM Z Open Editor** | 6.9+ | Extensão VS Code |

```sh
# Instalar Zowe CLI (caso necessário)
npm install -g @zowe/cli
zowe plugins install @zowe/rse-api-for-zowe-cli
```

### No z/OS (USS)

| Componente | Descrição |
|-----------|-----------|
| **IBM DBB** | 2.x ou 3.x instalado em `$DBB_HOME` |
| **dbb-zappbuild** | Clonado em `${WORKSPACE}/dbb-zappbuild/` |
| **Enterprise COBOL** | V6.3+ (biblioteca `IGY630.SIGYCOMP` ou similar) |
| **Enterprise PL/I** | V6.1+ (biblioteca `IEL610.SIBMZCMP` ou similar) |
| **HLASM** | (biblioteca `HLA.SASMMOD1` ou similar) |
| **Language Environment** | `CEE.SCEELKED`, `CEE.SCEEMAC` |

```sh
# Verificar DBB no z/OS (via Zowe SSH)
zowe uss issue ssh "echo \$DBB_HOME; \$DBB_HOME/bin/dbb --version"
```

---

## 3. Estrutura do Repositório

```
zopeneditor-sample-wazi/
├── COBOL/                    ← Fontes COBOL
│   ├── SAM1.cbl              ← Programa principal (chama SAM2)
│   ├── SAM2.cbl              ← Sub-rotina de atualização
│   ├── SAM1LIB.cbl           ← SAM1 com resolução de copybook via biblioteca
│   └── PCFDEMO.cbl           ← Demo de configuração de compilação
├── PLI/                      ← Fontes PL/I
│   ├── PSAM1.pli             ← Programa principal PL/I
│   ├── PSAM2.pli             ← Sub-rotina PL/I (compilada primeiro)
│   ├── PSAM1LIB.pli          ← Variante com include via biblioteca
│   └── MACSAMP.pli           ← Exemplo com macros PL/I
├── ASM/                      ← Fontes Assembler HLASM
│   ├── ASAM1.asm             ← Programa principal ASM
│   └── MASAM1.asm            ← Variante com macros
├── REXX/                     ← Scripts REXX
│   ├── RSAM1.rexx            ← Lê arquivos de entrada e gera saída combinada
│   └── FIB.rexx              ← Cálculo Fibonacci
├── COPYBOOK/                 ← Copybooks COBOL
├── INCLUDES/                 ← Includes PL/I
├── ASMCOPY/                  ← Copybooks Assembler
├── ASMMAC/                   ← Macros Assembler locais
├── JCL/                      ← JCLs de alocação, compilação e execução
├── application-conf/         ← Propriedades zAppBuild
│   ├── application.properties  ← Configuração geral + ordem de build
│   ├── Cobol.properties        ← Opções compilador COBOL
│   ├── PLI.properties          ← Opções compilador PL/I
│   ├── Assembler.properties    ← Opções montador HLASM
│   ├── REXX.properties         ← Opções scripts REXX
│   ├── file.properties         ← Mapeamentos fonte → script de build
│   ├── bind.properties         ← Configuração DB2 BIND (opcional)
│   └── datasets-sandbox.properties ← Bibliotecas do sistema z/OS
├── groovy/                   ← Scripts Groovy de build
│   ├── dbb-utilities.groovy      ← Utilitários compartilhados
│   ├── dbb-cobol-build.groovy    ← Build COBOL (SAM1, SAM2, SAM1LIB, PCFDEMO)
│   ├── dbb-pli-build.groovy      ← Build PL/I (PSAM1, PSAM2, PSAM1LIB, MACSAMP)
│   ├── dbb-asm-build.groovy      ← Build Assembler (ASAM1, MASAM1)
│   ├── dbb-rexx-build.groovy     ← Build REXX (RSAM1, FIB)
│   ├── dbb-full-build.groovy     ← Orquestrador: executa todos os tipos
│   └── dbb-sam-build.groovy      ← Build legado (SAM1+SAM2 apenas)
├── build.properties          ← Propriedades centrais DBB (HLQ, datasets, etc.)
├── dbb-app.yaml              ← Configuração zBuilder (DBB 3.x)
├── zapp.yaml                 ← Z Open Editor / User Build config
└── zowe/
    ├── dbb-validate.sh           ← Script de validação da integração DBB
    └── dbb-prepare-uss-folder.sh ← Prepara pasta USS e clona dbb-zappbuild
```

---

## 4. Configuração Inicial

### Passo 4.1 — Configurar o perfil Zowe

```sh
# Criar perfil RSE API (substitua pelos valores do seu ambiente)
zowe profiles create rse meuPerfil \
    --host meu-zos.empresa.com \
    --port 6800 \
    --user MEUUSER \
    --password MINHASENHA \
    --reject-unauthorized false

# Testar conectividade
zowe rse check status --rse-profile meuPerfil
```

### Passo 4.2 — Configurar o `build.properties`

Abra o arquivo [`build.properties`](build.properties) na raiz do repositório e ajuste:

```properties
# Substitua IBMUSER pelo seu user-id z/OS
hlq=IBMUSER

# Caminho USS onde o repositório foi clonado
workspace=/u/ibmuser/projects

# Biblioteca do compilador COBOL disponível no seu sistema
SIGYCOMP_V6=IGY630.SIGYCOMP

# Biblioteca do compilador PL/I disponível no seu sistema
IBMZPLI_V61=IEL610.SIBMZCMP

# Biblioteca HLASM
SASMMOD1=HLA.SASMMOD1
```

### Passo 4.3 — Configurar `datasets-sandbox.properties`

Abra [`application-conf/datasets-sandbox.properties`](application-conf/datasets-sandbox.properties) e verifique/ajuste as bibliotecas do sistema para o seu z/OS:

```properties
MACLIB=SYS1.MACLIB
SCEEMAC=CEE.SCEEMAC
SCEELKED=CEE.SCEELKED
SASMMOD1=HLA.SASMMOD1       # ← ajuste para o seu sistema
SIGYCOMP_V6=IGY630.SIGYCOMP # ← ajuste para o seu sistema
IBMZPLI_V61=IEL610.SIBMZCMP # ← ajuste para o seu sistema
```

### Passo 4.4 — Preparar a pasta USS e clonar dbb-zappbuild

```sh
# A partir da raiz do repositório local, execute:
export HLQ=IBMUSER
export USS_USER=ibmuser

# Prepara pasta USS e clona o repositório dbb-zappbuild
source zowe/dbb-prepare-uss-folder.sh
```

---

## 5. Configuração dos Datasets no z/OS

Antes do primeiro build, é necessário alocar os datasets MVS. Utilize o JCL de alocação incluído no repositório.

### Passo 5.1 — Alocar datasets COBOL

```sh
# Substitua USER1 pelo seu HLQ no ALLOCATE.jcl antes de submeter
zowe rse jobs submit local-file JCL/ALLOCATE.jcl

# Verificar resultado do job
zowe rse jobs list jobs --job-name "ALLOCATE" --owner IBMUSER
```

### Passo 5.2 — Alocar datasets PL/I

```sh
zowe rse jobs submit local-file JCL/PLIALLOC.jcl
```

### Passo 5.3 — Alocar datasets Assembler

```sh
zowe rse jobs submit local-file JCL/ASMALLOC.jcl
```

### Passo 5.4 — Alocar datasets REXX

```sh
zowe rse jobs submit local-file JCL/REXALLOC.jcl
```

> **Dica:** Os datasets são criados automaticamente pelos scripts Groovy de build caso não existam. A alocação via JCL é necessária apenas se você preferir controlar o volume, tipo e espaço manualmente.

---

## 6. Execução do Build Completo

### Passo 6.1 — Upload do repositório para USS

```sh
# A partir da raiz do repositório:
export HLQ=IBMUSER
export USS_USER=ibmuser

# Upload completo (todos os fontes e dependências)
zowe rse upload dir-to-uss . /u/ibmuser/projects/zopeneditor-sample \
    --recursive --rse-profile meuPerfil
```

### Passo 6.2 — Executar o build completo (todas as linguagens)

```sh
# Via SSH a partir do workstation local:
zowe uss issue ssh \
    "export DBB_HOME=/usr/lpp/IBM/dbb; \
     \$DBB_HOME/bin/groovyz \
     /u/ibmuser/projects/zopeneditor-sample/groovy/dbb-full-build.groovy \
     IBMUSER /u/ibmuser/projects/zopeneditor-sample"
```

**Ou diretamente no USS** (se você tiver acesso SSH/3270):

```sh
cd /u/ibmuser/projects/zopeneditor-sample
export DBB_HOME=/usr/lpp/IBM/dbb

$DBB_HOME/bin/groovyz groovy/dbb-full-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample
```

### Saída esperada

```
══════════════════════════════════════════════════════════════════════
 IBM DBB — Build Completo — zopeneditor-sample
══════════════════════════════════════════════════════════════════════
 HLQ       : IBMUSER
 Workspace : /u/ibmuser/projects/zopeneditor-sample
 Início    : 15/01/2025 09:30:00

 Linguagens selecionadas:
   COBOL    : SIM (IGY630.SIGYCOMP)
   PL/I     : SIM (IEL610.SIBMZCMP)
   Assembler: SIM (HLA.SASMMOD1)
   REXX     : SIM (modo EXEC)
══════════════════════════════════════════════════════════════════════
[...]
 RELATÓRIO DE BUILD — zopeneditor-sample
══════════════════════════════════════════════════════════════════════
 ✔ COBOL     : OK       (45s)
 ✔ PLI       : OK       (38s)
 ✔ ASSEMBLER : OK       (12s)
 ✔ REXX      : OK       (5s)
══════════════════════════════════════════════════════════════════════
 BUILD COMPLETO CONCLUÍDO COM SUCESSO
```

---

## 7. Execução por Tipo de Linguagem

### Build apenas COBOL

```sh
$DBB_HOME/bin/groovyz groovy/dbb-cobol-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample [COBOL.V6R2.SIGYCOMP]
```

Programas compilados: **SAM2** → **SAM1** → **SAM1LIB** → **PCFDEMO**

### Build apenas PL/I

```sh
$DBB_HOME/bin/groovyz groovy/dbb-pli-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample [IEL610.SIBMZCMP]
```

Programas compilados: **PSAM2** → **PSAM1** → **PSAM1LIB** → **MACSAMP**

### Build apenas Assembler

```sh
$DBB_HOME/bin/groovyz groovy/dbb-asm-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample [HLA.SASMMOD1]
```

Programas montados: **ASAM1** · **MASAM1**

### Build apenas REXX

```sh
# Modo EXEC (padrão — apenas copia para o dataset REXX)
$DBB_HOME/bin/groovyz groovy/dbb-rexx-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample

# Modo compilado (requer IBM REXX Compiler)
$DBB_HOME/bin/groovyz groovy/dbb-rexx-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample compiled
```

### Usando o orquestrador com opções

```sh
# Build de todas as linguagens exceto REXX
$DBB_HOME/bin/groovyz groovy/dbb-full-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample --skip-rexx

# Apenas PL/I, com compilador específico
$DBB_HOME/bin/groovyz groovy/dbb-full-build.groovy \
    IBMUSER /u/ibmuser/projects/zopeneditor-sample \
    --pli-only --pli-compiler IEL610.SIBMZCMP
```

---

## 8. User Build (build de arquivo único)

O User Build permite compilar um único arquivo diretamente do IBM Z Open Editor.

### Configurar o perfil de User Build no `zapp.yaml`

O arquivo [`zapp.yaml`](zapp.yaml) já contém perfis pré-configurados. Selecione o mais adequado:

| Perfil | Quando usar |
|--------|-------------|
| `zBuilder-userbuild` | DBB 3.x com zBuilder |
| `groovyz-userbuild` | DBB 2.x com dbb-zappbuild |
| `zowe-upload-userbuild2` | DBB v2 + zAppBuild via Zowe |

### Executar User Build pelo Z Open Editor

1. Abra o arquivo fonte (ex: `COBOL/SAM1.cbl`)
2. Pressione `Ctrl+Shift+P` → **IBM Z: Run User Build**
3. Selecione o perfil (ex: `zBuilder-userbuild`)
4. Na primeira execução, responda **Sim** ao upload completo

### Executar User Build via MCP (IBM Bob)

```
Pergunta para o Bob: "Execute o user build do arquivo SAM1.cbl"
```

O Bob usará a ferramenta `userbuild-run-user-build` automaticamente.

---

## 9. Validação da Integração

### Executar o script de validação

```sh
# A partir da raiz do repositório local:
./zowe/dbb-validate.sh IBMUSER /u/ibmuser/projects/zopeneditor-sample
```

O script verifica:
- ✔ Ambiente local (DBB_HOME, Zowe, Java)
- ✔ Estrutura completa do repositório
- ✔ Conectividade com o z/OS
- ✔ Existência dos datasets pós-build
- ✔ Membros load gerados (SAM1, SAM2, PSAM1, ASAM1…)
- ✔ Logs de build sem erros críticos

### Verificar resultados manualmente

```sh
# Listar load modules gerados
zowe rse list am "IBMUSER.SAMPLE.LOAD" --rse-profile meuPerfil

# Verificar log de compilação do SAM1
cat /u/ibmuser/projects/zopeneditor-sample/logs/compile-SAM1.cbl.log

# Ver relatório de build completo
cat /u/ibmuser/projects/zopeneditor-sample/logs/dbb-full-build-report.txt
```

### Executar os programas após o build

```sh
# Executar SAM1 (COBOL)
zowe rse jobs submit local-file JCL/RUN.jcl --rse-profile meuPerfil

# Executar PSAM1 (PL/I)
zowe rse jobs submit local-file JCL/RUNPSAM1.jcl --rse-profile meuPerfil

# Executar ASAM1 (Assembler)
zowe rse jobs submit local-file JCL/RUNASAM1.jcl --rse-profile meuPerfil
```

---

## 10. Solução de Problemas Comuns

### ❌ `DBB_HOME not set`

```sh
# Defina no USS antes de executar groovyz:
export DBB_HOME=/usr/lpp/IBM/dbb

# Para persistir, adicione ao ~/.profile no USS:
echo 'export DBB_HOME=/usr/lpp/IBM/dbb' >> ~/.profile
```

### ❌ `SIGYCOMP dataset not found`

Edite [`application-conf/datasets-sandbox.properties`](application-conf/datasets-sandbox.properties) e ajuste a propriedade `SIGYCOMP_V6` para o nome correto no seu z/OS.

### ❌ `RC=8` na compilação COBOL

Verifique o log em `logs/compile-SAM1.cbl.log`. Os erros mais comuns são:
- Copybook não encontrado → verifique se `IBMUSER.SAMPLE.COPY` foi alocado e populado
- Opção de compilador inválida → revise `cobol_compileParms` em [`application-conf/Cobol.properties`](application-conf/Cobol.properties)

### ❌ `SAM2 must be compiled before SAM1`

O script `dbb-cobol-build.groovy` já garante a ordem correta (SAM2 → SAM1). Se estiver usando zAppBuild diretamente, verifique se `cobol_fileBuildRank = 1 :: **/COBOL/SAM2.cbl` está em [`application-conf/file.properties`](application-conf/file.properties).

### ❌ `Link-edit failed` para PSAM1

PSAM1 depende de PSAM2. Verifique se PSAM2 foi compilado antes (o script `dbb-pli-build.groovy` garante isso) e se o objeto de PSAM2 está em `IBMUSER.SAMPLE.OBJ(PSAM2)`.

### ❌ `Zowe connection refused`

```sh
# Teste a conectividade:
zowe rse check status
# Se falhar, recrie o perfil:
zowe profiles create rse meuPerfil --host HOST --port 6800 --user USER --password SENHA
```

---

## 11. Referência de Propriedades

### `build.properties` (arquivo principal)

| Propriedade | Valor padrão | Descrição |
|------------|-------------|-----------|
| `hlq` | `IBMUSER` | High Level Qualifier dos datasets |
| `application` | `zopeneditor-sample` | Nome da aplicação |
| `workspace` | `/u/ibmuser/projects` | Diretório raiz USS |
| `cobol_compileParms` | `LIB,MAP,NODYN` | Opções compilador COBOL |
| `pli_compileParms` | `PP(INCLUDE(...))` | Opções compilador PL/I |
| `assembler_pgmParms` | `LIST,OBJECT` | Opções montador HLASM |

### Datasets gerados

| Dataset | Conteúdo |
|---------|----------|
| `HLQ.SAMPLE.COBOL` | Fontes COBOL |
| `HLQ.SAMPLE.PLI` | Fontes PL/I |
| `HLQ.SAMPLE.ASM` | Fontes Assembler |
| `HLQ.SAMPLE.REXX` | Scripts REXX |
| `HLQ.SAMPLE.COPY` | Copybooks COBOL |
| `HLQ.SAMPLE.PLINC` | Includes PL/I |
| `HLQ.SAMPLE.ASMCOPY` | Copybooks ASM |
| `HLQ.SAMPLE.OBJ` | Módulos objeto |
| `HLQ.SAMPLE.LOAD` | Load modules (executáveis) |

---

> **Suporte:** Para dúvidas sobre o IBM Z Open Editor e DBB, consulte a [documentação oficial do IBM DBB](https://www.ibm.com/docs/en/dbb) e o [IBM Z Open Editor](https://ibm.github.io/zopeneditor-about/).
