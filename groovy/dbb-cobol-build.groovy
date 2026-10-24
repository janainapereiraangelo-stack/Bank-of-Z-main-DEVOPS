/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************
 *
 * dbb-cobol-build.groovy
 * Script de build DBB para programas COBOL do repositório zopeneditor-sample.
 *
 * Programas processados:
 *   - SAM1.cbl  — Programa principal: atualiza arquivo de clientes via transações
 *   - SAM2.cbl  — Sub-rotina de atualização chamada por SAM1
 *   - SAM1LIB.cbl — Variante de SAM1 com resolução de copybook via biblioteca
 *   - PCFDEMO.cbl  — Demonstração de configuração de compilação
 *
 * Copybooks utilizados:
 *   - CUSTCOPY.cpy — Layout do registro de clientes
 *   - TRANREC.cpy  — Layout do registro de transações
 *
 * Uso:
 *   $DBB_HOME/bin/groovyz dbb-cobol-build.groovy <HLQ> <dirFonte> [<compiladorDS>]
 *
 * Exemplos:
 *   $DBB_HOME/bin/groovyz dbb-cobol-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample
 *   $DBB_HOME/bin/groovyz dbb-cobol-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample COBOL.V6R2.SIGYCOMP
 *******************************************************************************/

@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.util.*
import groovy.transform.*

// ---------------------------------------------------------------------------
// Carrega utilitários compartilhados
// ---------------------------------------------------------------------------
@Field def buildUtils = loadScript(new File("${new File(getClass().protectionDomain.codeSource.location.path).parent}/dbb-utilities.groovy"))

// ---------------------------------------------------------------------------
// Validação e leitura dos argumentos
// ---------------------------------------------------------------------------
if (args.length < 2) {
    println "ERRO: Argumentos insuficientes."
    println "Uso: groovyz dbb-cobol-build.groovy <HLQ> <dirFonte> [<compiladorDS>]"
    System.exit(8)
}

def hlq        = args[0]   // Ex.: IBMUSER
def sourceDir  = args[1]   // Ex.: /u/ibmuser/projects/zopeneditor-sample
def compilerDS = args.length > 2 ? args[2] : "IGY630.SIGYCOMP"
def linklib    = "CEE.SCEELKED"

// ---------------------------------------------------------------------------
// Definição dos nomes de datasets
// ---------------------------------------------------------------------------
def srcPDS  = "${hlq}.SAMPLE.COBOL"
def objPDS  = "${hlq}.SAMPLE.OBJ"
def loadPDS = "${hlq}.SAMPLE.LOAD"
def copyPDS = "${hlq}.SAMPLE.COPY"

// ---------------------------------------------------------------------------
// Caminhos dos arquivos de log
// ---------------------------------------------------------------------------
def logsDir = "${sourceDir}/logs"
new File(logsDir).mkdirs()

def logs = [
    "SAM2"    : "${logsDir}/compile-SAM2.cbl.log",
    "SAM1"    : "${logsDir}/compile-SAM1.cbl.log",
    "SAM1LIB" : "${logsDir}/compile-SAM1LIB.cbl.log",
    "PCFDEMO" : "${logsDir}/compile-PCFDEMO.cbl.log",
    "SAM2L"   : "${logsDir}/link-SAM2.log",
    "SAM1L"   : "${logsDir}/link-SAM1.log",
    "SAM1LIBL": "${logsDir}/link-SAM1LIB.log",
]

// ---------------------------------------------------------------------------
// Opções de alocação dos datasets
// ---------------------------------------------------------------------------
def srcOptions  = "cyl space(100,10) lrecl(80) dsorg(PO) recfm(F,B) blksize(32720) dsntype(library) msg(1) new"
def loadOptions = "cyl space(100,10) dsorg(PO) recfm(U) blksize(32720) dsntype(library) msg(1) new"

// ---------------------------------------------------------------------------
// Parâmetro de link-edit para cada programa
// ---------------------------------------------------------------------------
def linkCards = [:]
linkCards["SAM1"] = """
     INCLUDE OBJ(SAM1)
     INCLUDE OBJ(SAM2)
     ENTRY SAM1
     NAME SAM1(R)
"""
linkCards["SAM2"] = """
     INCLUDE OBJ(SAM2)
     NAME SAM2(R)
"""
linkCards["SAM1LIB"] = """
     INCLUDE OBJ(SAM1LIB)
     ENTRY SAM1LIB
     NAME SAM1LIB(R)
"""

// ---------------------------------------------------------------------------
// INÍCIO DO BUILD
// ---------------------------------------------------------------------------
println "=" * 70
println " IBM DBB — Build COBOL — zopeneditor-sample"
println "=" * 70
println " HLQ      : ${hlq}"
println " Fonte    : ${sourceDir}/COBOL"
println " Compiler : ${compilerDS}"
println " SrcPDS   : ${srcPDS}"
println " ObjPDS   : ${objPDS}"
println " LoadPDS  : ${loadPDS}"
println " CopyPDS  : ${copyPDS}"
println "=" * 70

// ---------------------------------------------------------------------------
// Passo 1 — Limpeza e recriação dos datasets
// ---------------------------------------------------------------------------
println "\n[PASSO 1] Limpando datasets anteriores..."
buildUtils.deleteDatasets(["$srcPDS", "$objPDS", "$loadPDS", "$copyPDS"] as String[])

println "[PASSO 2] Criando novos datasets..."
buildUtils.createDatasets([
    "$srcPDS" : "$srcOptions",
    "$objPDS" : "$srcOptions",
    "$loadPDS": "$loadOptions",
    "$copyPDS": "$srcOptions"
])

// ---------------------------------------------------------------------------
// Passo 2 — Cópia de fontes e copybooks USS → MVS
// ---------------------------------------------------------------------------
println "\n[PASSO 3] Copiando fontes COBOL para ${srcPDS}..."
new CopyToPDS().file(new File("${sourceDir}/COBOL/")).dataset(srcPDS).execute()

println "[PASSO 4] Copiando copybooks para ${copyPDS}..."
new CopyToPDS().file(new File("${sourceDir}/COPYBOOK/")).dataset(copyPDS).execute()

// ---------------------------------------------------------------------------
// Passo 3 — Compilação (SAM2 primeiro — é dependência de SAM1)
// ---------------------------------------------------------------------------
println "\n[PASSO 5] Compilando programas COBOL..."

// SAM2 deve ser compilado antes de SAM1
buildUtils.compileProgram(srcPDS, "SAM2",    compilerDS, copyPDS, objPDS, logs["SAM2"])
buildUtils.compileProgram(srcPDS, "SAM1",    compilerDS, copyPDS, objPDS, logs["SAM1"])
buildUtils.compileProgram(srcPDS, "SAM1LIB", compilerDS, copyPDS, objPDS, logs["SAM1LIB"])
buildUtils.compileProgram(srcPDS, "PCFDEMO", compilerDS, copyPDS, objPDS, logs["PCFDEMO"])

// ---------------------------------------------------------------------------
// Passo 4 — Link-Edit
// ---------------------------------------------------------------------------
println "\n[PASSO 6] Executando link-edit dos programas COBOL..."
buildUtils.linkProgram(loadPDS, "SAM2",    objPDS, linklib, linkCards["SAM2"],    logs["SAM2L"])
buildUtils.linkProgram(loadPDS, "SAM1",    objPDS, linklib, linkCards["SAM1"],    logs["SAM1L"])
buildUtils.linkProgram(loadPDS, "SAM1LIB", objPDS, linklib, linkCards["SAM1LIB"], logs["SAM1LIBL"])

// ---------------------------------------------------------------------------
// Sumário
// ---------------------------------------------------------------------------
println "\n" + "=" * 70
println " BUILD COBOL CONCLUÍDO COM SUCESSO"
println " Executáveis disponíveis em: ${loadPDS}"
println " Logs disponíveis em       : ${logsDir}"
println "=" * 70
