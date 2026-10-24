/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************
 *
 * dbb-full-build.groovy
 * Script Groovy orquestrador — Executa o build completo do repositório
 * zopeneditor-sample: COBOL → PL/I → Assembler → REXX, em sequência.
 *
 * Este script:
 *   1. Lê as propriedades de build.properties (ou do diretório especificado)
 *   2. Chama cada script de build por linguagem na ordem correta
 *   3. Produz um relatório consolidado de build ao final
 *
 * Uso:
 *   $DBB_HOME/bin/groovyz dbb-full-build.groovy <HLQ> <dirFonte> [opções]
 *
 * Opções:
 *   --cobol-only     Executa somente o build COBOL
 *   --pli-only       Executa somente o build PL/I
 *   --asm-only       Executa somente o build Assembler
 *   --rexx-only      Executa somente o build REXX
 *   --skip-rexx      Executa todos exceto REXX
 *   --cobol-compiler <DS>  Biblioteca do compilador COBOL (padrão: IGY630.SIGYCOMP)
 *   --pli-compiler   <DS>  Biblioteca do compilador PL/I  (padrão: IEL610.SIBMZCMP)
 *   --asm-lib        <DS>  Biblioteca HLASM               (padrão: HLA.SASMMOD1)
 *
 * Exemplos:
 *   # Build completo com padrões:
 *   $DBB_HOME/bin/groovyz dbb-full-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample
 *
 *   # Apenas COBOL, com compilador customizado:
 *   $DBB_HOME/bin/groovyz dbb-full-build.groovy IBMUSER /u/ibmuser/projects/zopeneditor-sample \
 *       --cobol-only --cobol-compiler COBOL.V6R2.SIGYCOMP
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
    println "Uso: groovyz dbb-full-build.groovy <HLQ> <dirFonte> [opções]"
    println ""
    println "Opções disponíveis:"
    println "  --cobol-only          Executa somente build COBOL"
    println "  --pli-only            Executa somente build PL/I"
    println "  --asm-only            Executa somente build Assembler"
    println "  --rexx-only           Executa somente build REXX"
    println "  --skip-rexx           Executa todos exceto REXX"
    println "  --cobol-compiler <DS> Biblioteca do compilador COBOL"
    println "  --pli-compiler   <DS> Biblioteca do compilador PL/I"
    println "  --asm-lib        <DS> Biblioteca HLASM"
    System.exit(8)
}

def hlq       = args[0]
def sourceDir = args[1]

// Valores padrão
def cobolCompilerDS = "IGY630.SIGYCOMP"
def pliCompilerDS   = "IEL610.SIBMZCMP"
def asmLibDS        = "HLA.SASMMOD1"

// Flags de controle
def buildCobol = true
def buildPLI   = true
def buildAsm   = true
def buildRexx  = true

// Analisa argumentos opcionais
for (int i = 2; i < args.length; i++) {
    switch (args[i]) {
        case "--cobol-only":
            buildPLI = false; buildAsm = false; buildRexx = false; break
        case "--pli-only":
            buildCobol = false; buildAsm = false; buildRexx = false; break
        case "--asm-only":
            buildCobol = false; buildPLI = false; buildRexx = false; break
        case "--rexx-only":
            buildCobol = false; buildPLI = false; buildAsm = false; break
        case "--skip-rexx":
            buildRexx = false; break
        case "--cobol-compiler":
            if (i + 1 < args.length) cobolCompilerDS = args[++i]; break
        case "--pli-compiler":
            if (i + 1 < args.length) pliCompilerDS = args[++i]; break
        case "--asm-lib":
            if (i + 1 < args.length) asmLibDS = args[++i]; break
        default:
            println "AVISO: Opção desconhecida '${args[i]}' — ignorada."
    }
}

// ---------------------------------------------------------------------------
// Caminhos dos scripts por linguagem (relativo a este script)
// ---------------------------------------------------------------------------
def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent

// ---------------------------------------------------------------------------
// Variáveis de controle do relatório
// ---------------------------------------------------------------------------
def buildStartTime = new Date()
def buildResults   = [:]   // linguagem → [status, duracao]
def buildErrors    = []

// ---------------------------------------------------------------------------
// Utilitário para executar sub-scripts
// ---------------------------------------------------------------------------
def runBuildScript = { String scriptName, String[] scriptArgs ->
    def scriptFile = new File("${scriptDir}/${scriptName}")
    if (!scriptFile.exists()) {
        println "ERRO: Script ${scriptFile.absolutePath} não encontrado."
        return "FALHOU"
    }
    try {
        def t0 = System.currentTimeMillis()
        def loader = new com.ibm.dbb.groovy.ScriptLoader()
        loader.load(scriptFile, scriptArgs)
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        return [status: "OK", duracao: "${elapsed}s"]
    } catch (Exception e) {
        buildErrors << "${scriptName}: ${e.message}"
        return [status: "FALHOU", duracao: "?"]
    }
}

// ---------------------------------------------------------------------------
// INÍCIO DO BUILD COMPLETO
// ---------------------------------------------------------------------------
println "=" * 70
println " IBM DBB — Build Completo — zopeneditor-sample"
println "=" * 70
println " HLQ       : ${hlq}"
println " Workspace : ${sourceDir}"
println " Início    : ${buildStartTime.format('dd/MM/yyyy HH:mm:ss')}"
println ""
println " Linguagens selecionadas:"
println "   COBOL    : ${buildCobol ? 'SIM (' + cobolCompilerDS + ')' : 'NÃO'}"
println "   PL/I     : ${buildPLI   ? 'SIM (' + pliCompilerDS   + ')' : 'NÃO'}"
println "   Assembler: ${buildAsm   ? 'SIM (' + asmLibDS        + ')' : 'NÃO'}"
println "   REXX     : ${buildRexx  ? 'SIM (modo EXEC)'             : 'NÃO'}"
println "=" * 70

// ---------------------------------------------------------------------------
// Passo 1 — Build COBOL
// ---------------------------------------------------------------------------
if (buildCobol) {
    println "\n" + "-" * 50
    println " Iniciando build COBOL..."
    println "-" * 50
    buildResults["COBOL"] = runBuildScript("dbb-cobol-build.groovy",
        [hlq, sourceDir, cobolCompilerDS] as String[])
    if (buildResults["COBOL"].status != "OK") {
        buildErrors << "BUILD COBOL falhou — verifique ${sourceDir}/logs/"
    }
}

// ---------------------------------------------------------------------------
// Passo 2 — Build PL/I
// ---------------------------------------------------------------------------
if (buildPLI) {
    println "\n" + "-" * 50
    println " Iniciando build PL/I..."
    println "-" * 50
    buildResults["PLI"] = runBuildScript("dbb-pli-build.groovy",
        [hlq, sourceDir, pliCompilerDS] as String[])
    if (buildResults["PLI"].status != "OK") {
        buildErrors << "BUILD PL/I falhou — verifique ${sourceDir}/logs/"
    }
}

// ---------------------------------------------------------------------------
// Passo 3 — Build Assembler
// ---------------------------------------------------------------------------
if (buildAsm) {
    println "\n" + "-" * 50
    println " Iniciando build Assembler (HLASM)..."
    println "-" * 50
    buildResults["ASSEMBLER"] = runBuildScript("dbb-asm-build.groovy",
        [hlq, sourceDir, asmLibDS] as String[])
    if (buildResults["ASSEMBLER"].status != "OK") {
        buildErrors << "BUILD ASSEMBLER falhou — verifique ${sourceDir}/logs/"
    }
}

// ---------------------------------------------------------------------------
// Passo 4 — Build REXX
// ---------------------------------------------------------------------------
if (buildRexx) {
    println "\n" + "-" * 50
    println " Iniciando build REXX..."
    println "-" * 50
    buildResults["REXX"] = runBuildScript("dbb-rexx-build.groovy",
        [hlq, sourceDir] as String[])
    if (buildResults["REXX"].status != "OK") {
        buildErrors << "BUILD REXX falhou — verifique ${sourceDir}/logs/"
    }
}

// ---------------------------------------------------------------------------
// Relatório Consolidado
// ---------------------------------------------------------------------------
def buildEndTime = new Date()
def totalSec = ((buildEndTime.time - buildStartTime.time) / 1000).toInteger()

// Escreve relatório em arquivo
def reportFile = "${sourceDir}/logs/dbb-full-build-report.txt"
def report = new File(reportFile)
report.getParentFile().mkdirs()

def reportText = new StringBuilder()
reportText << "=" * 70 << "\n"
reportText << " RELATÓRIO DE BUILD — zopeneditor-sample\n"
reportText << "=" * 70 << "\n"
reportText << " HLQ       : ${hlq}\n"
reportText << " Workspace : ${sourceDir}\n"
reportText << " Início    : ${buildStartTime.format('dd/MM/yyyy HH:mm:ss')}\n"
reportText << " Término   : ${buildEndTime.format('dd/MM/yyyy HH:mm:ss')}\n"
reportText << " Duração   : ${totalSec}s\n"
reportText << "\n RESULTADOS POR LINGUAGEM:\n"
reportText << " " + "-" * 40 + "\n"

buildResults.each { lang, result ->
    def statusIcon = result.status == "OK" ? "✔" : "✘"
    reportText << " ${statusIcon} ${lang.padRight(12)} : ${result.status.padRight(8)} (${result.duracao})\n"
}

reportText << " " + "-" * 40 + "\n"

if (buildErrors) {
    reportText << "\n ERROS ENCONTRADOS:\n"
    buildErrors.each { err -> reportText << "   • ${err}\n" }
} else {
    reportText << "\n Nenhum erro encontrado. Todos os builds foram bem-sucedidos!\n"
}

reportText << "\n DATASETS GERADOS:\n"
if (buildCobol || buildPLI || buildAsm) {
    reportText << "   Load modules: ${hlq}.SAMPLE.LOAD\n"
    reportText << "   Objetos     : ${hlq}.SAMPLE.OBJ\n"
}
if (buildCobol)  reportText << "   Fontes COBOL: ${hlq}.SAMPLE.COBOL\n"
if (buildPLI)    reportText << "   Fontes PL/I : ${hlq}.SAMPLE.PLI\n"
if (buildAsm)    reportText << "   Fontes ASM  : ${hlq}.SAMPLE.ASM\n"
if (buildRexx)   reportText << "   Fontes REXX : ${hlq}.SAMPLE.REXX\n"

reportText << "=" * 70 << "\n"

report.text = reportText.toString()

// Exibe o relatório no console
println "\n" + reportText.toString()
println " Relatório salvo em: ${reportFile}"

// Retorna código de saída não-zero se houver falhas
if (buildErrors) {
    System.exit(8)
}
