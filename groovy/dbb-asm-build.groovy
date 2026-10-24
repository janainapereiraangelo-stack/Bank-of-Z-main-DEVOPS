/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************
 *
 * dbb-asm-build.groovy
 * Script de build DBB para programas Assembler (HLASM) do repositório zopeneditor-sample.
 *
 * Programas processados:
 *   - ASAM1.asm  — Lê um arquivo e escreve saída com informação de registro/coluna
 *   - MASAM1.asm — Variante de ASAM1 com uso de macros
 *
 * Copybooks Assembler utilizados:
 *   - ASMCOPY/REGISTRS.asm — Definição de registros do sistema
 *
 * Macros utilizadas:
 *   - ASMMAC/ — Diretório de macros locais
 *
 * Uso:
 *   $DBB_HOME/bin/groovyz dbb-asm-build.groovy <HLQ> <dirFonte> [<maclib>]
 *
 * Exemplos:
 *   $DBB_HOME/bin/groovyz dbb-asm-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample
 *   $DBB_HOME/bin/groovyz dbb-asm-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample HLA.SASMMOD1
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
    println "Uso: groovyz dbb-asm-build.groovy <HLQ> <dirFonte> [<maclibDS>]"
    System.exit(8)
}

def hlq        = args[0]
def sourceDir  = args[1]
def assemblerDS = "HLA.SASMMOD1"   // HLASM load library
def sceemac    = "CEE.SCEEMAC"     // Language Environment macros
def maclib     = "SYS1.MACLIB"     // System macro library
def linklib    = "CEE.SCEELKED"    // LE link library

// Override HLASM DS se fornecido
if (args.length > 2) assemblerDS = args[2]

// ---------------------------------------------------------------------------
// Definição dos nomes de datasets
// ---------------------------------------------------------------------------
def srcPDS     = "${hlq}.SAMPLE.ASM"
def objPDS     = "${hlq}.SAMPLE.OBJ"
def loadPDS    = "${hlq}.SAMPLE.LOAD"
def asmCopyPDS = "${hlq}.SAMPLE.ASMCOPY"
def asmMacPDS  = "${hlq}.SAMPLE.MACLIB"

// ---------------------------------------------------------------------------
// Caminhos dos arquivos de log
// ---------------------------------------------------------------------------
def logsDir = "${sourceDir}/logs"
new File(logsDir).mkdirs()

// ---------------------------------------------------------------------------
// Opções de alocação dos datasets
// ---------------------------------------------------------------------------
def srcOptions  = "cyl space(100,10) lrecl(80) dsorg(PO) recfm(F,B) blksize(32720) dsntype(library) msg(1) new"
def loadOptions = "cyl space(100,10) dsorg(PO) recfm(U) blksize(32720) dsntype(library) msg(1) new"

// ---------------------------------------------------------------------------
// Parâmetros de link-edit para cada programa
// ---------------------------------------------------------------------------
def linkCards = [:]
linkCards["ASAM1"] = """
     INCLUDE OBJ(ASAM1)
     ENTRY ASAM1
     NAME ASAM1(R)
"""
linkCards["MASAM1"] = """
     INCLUDE OBJ(MASAM1)
     ENTRY MASAM1
     NAME MASAM1(R)
"""

// ---------------------------------------------------------------------------
// INÍCIO DO BUILD
// ---------------------------------------------------------------------------
println "=" * 70
println " IBM DBB — Build Assembler (HLASM) — zopeneditor-sample"
println "=" * 70
println " HLQ         : ${hlq}"
println " Fonte       : ${sourceDir}/ASM"
println " AssemblerDS : ${assemblerDS}"
println " SrcPDS      : ${srcPDS}"
println " ObjPDS      : ${objPDS}"
println " LoadPDS     : ${loadPDS}"
println " AsmCopyPDS  : ${asmCopyPDS}"
println " AsmMacPDS   : ${asmMacPDS}"
println "=" * 70

// ---------------------------------------------------------------------------
// Passo 1 — Limpeza e recriação dos datasets
// ---------------------------------------------------------------------------
println "\n[PASSO 1] Limpando datasets anteriores..."
buildUtils.deleteDatasets(["$srcPDS", "$asmCopyPDS", "$asmMacPDS", "$loadPDS"] as String[])

println "[PASSO 2] Criando novos datasets..."
buildUtils.createDatasets([
    "$srcPDS"    : "$srcOptions",
    "$asmCopyPDS": "$srcOptions",
    "$asmMacPDS" : "$srcOptions",
    "$loadPDS"   : "$loadOptions"
])

// ---------------------------------------------------------------------------
// Passo 2 — Cópia de fontes, copybooks e macros USS → MVS
// ---------------------------------------------------------------------------
println "\n[PASSO 3] Copiando fontes Assembler para ${srcPDS}..."
new CopyToPDS().file(new File("${sourceDir}/ASM/")).dataset(srcPDS).execute()

println "[PASSO 4] Copiando copybooks Assembler para ${asmCopyPDS}..."
new CopyToPDS().file(new File("${sourceDir}/ASMCOPY/")).dataset(asmCopyPDS).execute()

// Copia macros locais se existirem
def asmMacDir = new File("${sourceDir}/ASMMAC/")
if (asmMacDir.exists() && asmMacDir.list().length > 0) {
    println "[PASSO 4b] Copiando macros locais para ${asmMacPDS}..."
    new CopyToPDS().file(asmMacDir).dataset(asmMacPDS).execute()
} else {
    println "[PASSO 4b] Nenhuma macro local encontrada em ASMMAC/ — ignorado."
}

// ---------------------------------------------------------------------------
// Passo 3 — Montagem (Assembly)
// ---------------------------------------------------------------------------
println "\n[PASSO 5] Montando programas Assembler..."
assembleProgram(srcPDS, "ASAM1",  objPDS, asmCopyPDS, asmMacPDS,
                assemblerDS, sceemac, maclib, "${logsDir}/compile-ASAM1.asm.log")

assembleProgram(srcPDS, "MASAM1", objPDS, asmCopyPDS, asmMacPDS,
                assemblerDS, sceemac, maclib, "${logsDir}/compile-MASAM1.asm.log")

// ---------------------------------------------------------------------------
// Passo 4 — Link-Edit
// ---------------------------------------------------------------------------
println "\n[PASSO 6] Executando link-edit dos programas Assembler..."
buildUtils.linkProgram(loadPDS, "ASAM1",  objPDS, linklib, linkCards["ASAM1"],  "${logsDir}/link-ASAM1.log")
buildUtils.linkProgram(loadPDS, "MASAM1", objPDS, linklib, linkCards["MASAM1"], "${logsDir}/link-MASAM1.log")

// ---------------------------------------------------------------------------
// Sumário
// ---------------------------------------------------------------------------
println "\n" + "=" * 70
println " BUILD ASSEMBLER CONCLUÍDO COM SUCESSO"
println " Executáveis disponíveis em: ${loadPDS}"
println " Logs disponíveis em       : ${logsDir}"
println "=" * 70

// ---------------------------------------------------------------------------
// Método auxiliar para montar um programa HLASM
// ---------------------------------------------------------------------------
def assembleProgram(String srcDS, String member, String objDS,
                    String asmCopyDS, String asmMacDS,
                    String assemblerDS, String sceemac, String maclib,
                    String logFile) {

    def tempOptions = "cyl space(5,5) unit(vio) new"
    println "ASSEMBLE: ${srcDS}(${member}) -> ${objDS}(${member})"

    def assemble = new MVSExec().pgm("ASMA90").parm("OBJECT,LIST,RENT")
    assemble.dd(new DDStatement().name("TASKLIB").dsn("${assemblerDS}").options("shr"))
    assemble.dd(new DDStatement().name("SYSIN").dsn("${srcDS}(${member})").options("shr"))
    // SYSLIB concatenation: cópia local, macros locais, LE macros, sistema
    assemble.dd(new DDStatement().name("SYSLIB").dsn("${asmCopyDS}").options("shr"))
    assemble.dd(new DDStatement().dsn("${asmMacDS}").options("shr"))
    assemble.dd(new DDStatement().dsn("${sceemac}").options("shr"))
    assemble.dd(new DDStatement().dsn("${maclib}").options("shr"))
    assemble.dd(new DDStatement().name("SYSLIN").dsn("${objDS}(${member})").options("shr"))
    assemble.dd(new DDStatement().name("SYSUT1").options(tempOptions))
    assemble.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
    assemble.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(logFile)))

    def rc = assemble.execute()
    if (rc > 4) {
        println " ERRO: ${member} — montagem falhou! RC=${rc}"
        println " Verifique o log: ${logFile}"
        System.exit(rc)
    } else {
        println " OK: ${member} montado com sucesso. RC=${rc}"
    }
    return rc
}
