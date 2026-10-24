/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************
 *
 * dbb-rexx-build.groovy
 * Script de build DBB para programas REXX do repositório zopeneditor-sample.
 *
 * Programas processados:
 *   - RSAM1.rexx — Lê datasets de entrada e escreve saída combinada
 *   - FIB.rexx   — Exemplo de cálculo Fibonacci em REXX
 *
 * Observação: Scripts REXX podem ser utilizados como EXEC ou compilados com
 * o Compilador REXX da IBM para melhor desempenho. Este script suporta ambos
 * os modos:
 *   - Modo EXEC (padrão): apenas copia o fonte para o dataset de destino.
 *   - Modo COMPILED: compila com o IBM REXX Compiler se disponível.
 *
 * Uso:
 *   $DBB_HOME/bin/groovyz dbb-rexx-build.groovy <HLQ> <dirFonte> [compiled]
 *
 * Exemplos:
 *   $DBB_HOME/bin/groovyz dbb-rexx-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample
 *   $DBB_HOME/bin/groovyz dbb-rexx-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample compiled
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
    println "Uso: groovyz dbb-rexx-build.groovy <HLQ> <dirFonte> [compiled]"
    System.exit(8)
}

def hlq       = args[0]
def sourceDir = args[1]
def compiled  = (args.length > 2 && args[2].toLowerCase() == "compiled")

// ---------------------------------------------------------------------------
// Definição dos nomes de datasets
// ---------------------------------------------------------------------------
def rexxSrcPDS  = "${hlq}.SAMPLE.REXX"    // dataset destino (EXEC REXX)
def rexxExecPDS = "${hlq}.SAMPLE.CEXEC"   // dataset de compiled REXX (CEXEC)
def rexxIncPDS  = "${hlq}.SAMPLE.REXXINC" // includes REXX

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
// INÍCIO DO BUILD
// ---------------------------------------------------------------------------
println "=" * 70
println " IBM DBB — Build REXX — zopeneditor-sample"
println "=" * 70
println " HLQ        : ${hlq}"
println " Fonte      : ${sourceDir}/REXX"
println " Modo       : ${compiled ? 'COMPILED (IBM REXX Compiler)' : 'EXEC (cópia direta)'}"
println " RexxSrcPDS : ${rexxSrcPDS}"
if (compiled) {
    println " RexxExecPDS: ${rexxExecPDS}"
}
println "=" * 70

// ---------------------------------------------------------------------------
// Passo 1 — Limpeza e recriação dos datasets
// ---------------------------------------------------------------------------
println "\n[PASSO 1] Limpando datasets anteriores..."
def datasetsToDelete = ["$rexxSrcPDS", "$rexxIncPDS"]
if (compiled) datasetsToDelete << "$rexxExecPDS"
buildUtils.deleteDatasets(datasetsToDelete as String[])

println "[PASSO 2] Criando novos datasets..."
def datasetsToCreate = ["$rexxSrcPDS": "$srcOptions", "$rexxIncPDS": "$srcOptions"]
if (compiled) datasetsToCreate["$rexxExecPDS"] = "$loadOptions"
buildUtils.createDatasets(datasetsToCreate)

// ---------------------------------------------------------------------------
// Passo 2 — Cópia de fontes USS → MVS
// ---------------------------------------------------------------------------
println "\n[PASSO 3] Copiando fontes REXX para ${rexxSrcPDS}..."
new CopyToPDS().file(new File("${sourceDir}/REXX/")).dataset(rexxSrcPDS).execute()

// Copia includes REXX se existirem
def rexxIncDir = new File("${sourceDir}/REXXINC/")
if (rexxIncDir.exists() && rexxIncDir.list().length > 0) {
    println "[PASSO 4] Copiando includes REXX para ${rexxIncPDS}..."
    new CopyToPDS().file(rexxIncDir).dataset(rexxIncPDS).execute()
} else {
    println "[PASSO 4] Nenhum include REXX encontrado em REXXINC/ — ignorado."
}

// ---------------------------------------------------------------------------
// Passo 3 — Compilação (opcional) ou deploy como EXEC
// ---------------------------------------------------------------------------
if (compiled) {
    println "\n[PASSO 5] Compilando scripts REXX com IBM REXX Compiler..."
    // Verifica se o compilador REXX está disponível
    def rexxCompilerDS = "REXX.V1R4.SFANLMD"
    compileREXX(rexxSrcPDS, "RSAM1", rexxExecPDS, rexxIncPDS, rexxCompilerDS,
                "${logsDir}/compile-RSAM1.rexx.log")
    compileREXX(rexxSrcPDS, "FIB",   rexxExecPDS, rexxIncPDS, rexxCompilerDS,
                "${logsDir}/compile-FIB.rexx.log")
} else {
    println "\n[PASSO 5] Modo EXEC — scripts REXX copiados para ${rexxSrcPDS}."
    println "          Para executar: //STEP EXEC PGM=IKJEFT01"
    println "          //SYSPROC  DD  DSN=${rexxSrcPDS},DISP=SHR"
    println "          //SYSTSPRT DD  SYSOUT=*"
    println "          //SYSTSIN  DD  *"
    println "            %RSAM1"
    println "          /*"
}

// ---------------------------------------------------------------------------
// Sumário
// ---------------------------------------------------------------------------
println "\n" + "=" * 70
println " BUILD REXX CONCLUÍDO COM SUCESSO"
println " Fontes disponíveis em: ${rexxSrcPDS}"
if (compiled) println " Compiled execs em    : ${rexxExecPDS}"
println " Logs disponíveis em  : ${logsDir}"
println ""
println " ATENÇÃO: Scripts REXX no modo EXEC não precisam de link-edit."
println " Execute diretamente via ISPF, JCL com IKJEFT01, ou linha de comando TSO."
println "=" * 70

// ---------------------------------------------------------------------------
// Método auxiliar para compilar um script REXX com o IBM REXX Compiler
// ---------------------------------------------------------------------------
def compileREXX(String srcDS, String member, String execDS, String incDS,
                String compilerDS, String logFile) {

    def tempOptions = "cyl space(5,5) unit(vio) new"
    println "COMPILE REXX: ${srcDS}(${member}) -> ${execDS}(${member})"

    def compile = new MVSExec().pgm("REXXCOMP").parm("OBJECT PRINT XREF CEXEC")
    compile.dd(new DDStatement().name("TASKLIB").dsn("${compilerDS}").options("shr"))
    compile.dd(new DDStatement().name("SYSIN").dsn("${srcDS}(${member})").options("shr"))
    compile.dd(new DDStatement().name("SYSLIB").dsn("${incDS}").options("shr"))
    compile.dd(new DDStatement().name("SYSUT1").options(tempOptions))
    compile.dd(new DDStatement().name("SYSEXEC").dsn("${execDS}(${member})").options("shr"))
    compile.dd(new DDStatement().name("SYSPRINT").options(tempOptions))
    compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File(logFile)))

    def rc = compile.execute()
    if (rc > 4) {
        println " ATENÇÃO: ${member} — compilação REXX falhou (RC=${rc})."
        println " O script pode ainda ser executado no modo EXEC (não compilado)."
        println " Verifique o log: ${logFile}"
        // Não interrompe o build — REXX pode executar não-compilado
    } else {
        println " OK: ${member} compilado com sucesso. RC=${rc}"
    }
    return rc
}
