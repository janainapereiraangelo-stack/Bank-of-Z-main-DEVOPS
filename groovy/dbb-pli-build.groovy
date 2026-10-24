/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************
 *
 * dbb-pli-build.groovy
 * Script de build DBB para programas PL/I do repositório zopeneditor-sample.
 *
 * Programas processados:
 *   - PSAM2.pli   — Sub-rotina de cálculo (compilada primeiro, pois é dependência de PSAM1)
 *   - PSAM1.pli   — Programa principal: gera relatório de clientes com totais
 *   - PSAM1LIB.pli — Variante com resolução de include via biblioteca
 *   - MACSAMP.pli  — Exemplo com uso de macros PL/I
 *
 * Includes utilizados:
 *   - CUSTPLI.inc  — Layout do registro de clientes em PL/I
 *   - BALSTATS.inc — Estatísticas de saldos
 *
 * Uso:
 *   $DBB_HOME/bin/groovyz dbb-pli-build.groovy <HLQ> <dirFonte> [<compiladorDS>]
 *
 * Exemplos:
 *   $DBB_HOME/bin/groovyz dbb-pli-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample
 *   $DBB_HOME/bin/groovyz dbb-pli-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample IEL610.SIBMZCMP
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
    println "Uso: groovyz dbb-pli-build.groovy <HLQ> <dirFonte> [<compiladorPLIDS>]"
    System.exit(8)
}

def hlq         = args[0]
def sourceDir   = args[1]
def compilerDS  = args.length > 2 ? args[2] : "IEL610.SIBMZCMP"
def linklib     = "CEE.SCEELKED"

// ---------------------------------------------------------------------------
// Definição dos nomes de datasets
// ---------------------------------------------------------------------------
def srcPDS  = "${hlq}.SAMPLE.PLI"
def objPDS  = "${hlq}.SAMPLE.OBJ"
def loadPDS = "${hlq}.SAMPLE.LOAD"
def incPDS  = "${hlq}.SAMPLE.PLINC"

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
// Parâmetro de link-edit para cada programa
// ---------------------------------------------------------------------------
def linkCards = [:]
linkCards["PSAM1"] = """
     INCLUDE OBJ(PSAM1)
     INCLUDE OBJ(PSAM2)
     ENTRY PSAM1
     NAME PSAM1(R)
"""
linkCards["PSAM2"] = """
     INCLUDE OBJ(PSAM2)
     NAME PSAM2(R)
"""
linkCards["PSAM1LIB"] = """
     INCLUDE OBJ(PSAM1LIB)
     INCLUDE OBJ(PSAM2)
     ENTRY PSAM1LIB
     NAME PSAM1LIB(R)
"""

// ---------------------------------------------------------------------------
// INÍCIO DO BUILD
// ---------------------------------------------------------------------------
println "=" * 70
println " IBM DBB — Build PL/I — zopeneditor-sample"
println "=" * 70
println " HLQ      : ${hlq}"
println " Fonte    : ${sourceDir}/PLI"
println " Compiler : ${compilerDS}"
println " SrcPDS   : ${srcPDS}"
println " ObjPDS   : ${objPDS}"
println " LoadPDS  : ${loadPDS}"
println " IncPDS   : ${incPDS}"
println "=" * 70

// ---------------------------------------------------------------------------
// Passo 1 — Limpeza e recriação dos datasets
// ---------------------------------------------------------------------------
println "\n[PASSO 1] Limpando datasets anteriores..."
buildUtils.deleteDatasets(["$srcPDS", "$incPDS", "$loadPDS"] as String[])

println "[PASSO 2] Criando novos datasets..."
buildUtils.createDatasets([
    "$srcPDS" : "$srcOptions",
    "$incPDS" : "$srcOptions",
    "$loadPDS": "$loadOptions"
])

// ---------------------------------------------------------------------------
// Passo 2 — Cópia de fontes e includes USS → MVS
// ---------------------------------------------------------------------------
println "\n[PASSO 3] Copiando fontes PL/I para ${srcPDS}..."
new CopyToPDS().file(new File("${sourceDir}/PLI/")).dataset(srcPDS).execute()

println "[PASSO 4] Copiando includes PL/I para ${incPDS}..."
new CopyToPDS().file(new File("${sourceDir}/INCLUDES/")).dataset(incPDS).execute()

// ---------------------------------------------------------------------------
// Passo 3 — Compilação PL/I
// ---------------------------------------------------------------------------
// Opções base do compilador PL/I
def pliCompileParms = "PP(INCLUDE('ID(++INCLUDE)'))"

println "\n[PASSO 5] Compilando programas PL/I..."
println "         Parâmetros de compilação: ${pliCompileParms}"

// PSAM2 deve ser compilado primeiro (é dependência de PSAM1)
compilePLI(hlq, sourceDir, compilerDS, linklib, srcPDS, incPDS, objPDS,
           "PSAM2", pliCompileParms, "${logsDir}/compile-PSAM2.pli.log")

compilePLI(hlq, sourceDir, compilerDS, linklib, srcPDS, incPDS, objPDS,
           "PSAM1", pliCompileParms, "${logsDir}/compile-PSAM1.pli.log")

compilePLI(hlq, sourceDir, compilerDS, linklib, srcPDS, incPDS, objPDS,
           "PSAM1LIB", pliCompileParms, "${logsDir}/compile-PSAM1LIB.pli.log")

compilePLI(hlq, sourceDir, compilerDS, linklib, srcPDS, incPDS, objPDS,
           "MACSAMP", pliCompileParms, "${logsDir}/compile-MACSAMP.pli.log")

// ---------------------------------------------------------------------------
// Passo 4 — Link-Edit
// ---------------------------------------------------------------------------
println "\n[PASSO 6] Executando link-edit dos programas PL/I..."

// PSAM2 link-edit independente
buildUtils.linkProgram(loadPDS, "PSAM2",    objPDS, linklib, linkCards["PSAM2"],    "${logsDir}/link-PSAM2.log")
// PSAM1 inclui PSAM2 estaticamente
buildUtils.linkProgram(loadPDS, "PSAM1",    objPDS, linklib, linkCards["PSAM1"],    "${logsDir}/link-PSAM1.log")
buildUtils.linkProgram(loadPDS, "PSAM1LIB", objPDS, linklib, linkCards["PSAM1LIB"], "${logsDir}/link-PSAM1LIB.log")

// ---------------------------------------------------------------------------
// Sumário
// ---------------------------------------------------------------------------
println "\n" + "=" * 70
println " BUILD PL/I CONCLUÍDO COM SUCESSO"
println " Executáveis disponíveis em: ${loadPDS}"
println " Logs disponíveis em       : ${logsDir}"
println "=" * 70

// ---------------------------------------------------------------------------
// Método auxiliar para compilar um programa PL/I
// ---------------------------------------------------------------------------
def compilePLI(String hlq, String sourceDir, String compilerDS, String linklib,
               String srcDS, String incDS, String objDS,
               String member, String compileParms, String logFile) {

    def tempOptions = "cyl space(5,5) unit(vio) new"

    println "COMPILE PL/I: ${srcDS}(${member}) -> ${objDS}(${member})"

    def compile = new MVSExec().pgm("IBMZPLI").parm(compileParms)
    compile.dd(new DDStatement().name("TASKLIB").dsn("${compilerDS}").options("shr"))
    compile.dd(new DDStatement().name("SYSIN").dsn("${srcDS}(${member})").options("shr"))
    compile.dd(new DDStatement().name("SYSLIB").dsn("${incDS}").options("shr"))
    compile.dd(new DDStatement().name("SYSLIN").dsn("${objDS}(${member})").options("shr"))
    compile.dd(new DDStatement().name("SYSUT1").options(tempOptions))
    compile.dd(new DDStatement().name("SYSUT2").options(tempOptions))
    compile.dd(new DDStatement().name("SYSUT3").options(tempOptions))
    compile.dd(new DDStatement().name("SYSUT4").options(tempOptions))
    compile.dd(new DDStatement().name("SYSUT5").options(tempOptions))
    compile.dd(new DDStatement().name("SYSUT6").options(tempOptions))
    compile.dd(new DDStatement().name("SYSUT7").options(tempOptions))
    compile.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
    compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(logFile)))

    def rc = compile.execute()
    if (rc > 4) {
        println " ERRO: ${member} — compilação falhou! RC=${rc}"
        println " Verifique o log: ${logFile}"
        System.exit(rc)
    } else {
        println " OK: ${member} compilado com sucesso. RC=${rc}"
    }
    return rc
}
