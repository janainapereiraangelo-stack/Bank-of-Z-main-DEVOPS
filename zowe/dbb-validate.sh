#!/bin/sh
###############################################################################
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2021, 2025. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
###############################################################################
#
# dbb-validate.sh
# Script de validação da integração DBB para o repositório zopeneditor-sample.
#
# Este script verifica:
#   1. Disponibilidade do DBB_HOME e das ferramentas necessárias
#   2. Estrutura do repositório (fontes, copybooks, includes, etc.)
#   3. Conectividade Zowe com o z/OS
#   4. Existência dos datasets no z/OS (após alocação)
#   5. Resultados dos logs de build (RC aceitáveis)
#   6. Existência dos load modules gerados
#
# Ao final, exibe um relatório consolidado de validação.
#
# Uso:
#   ./zowe/dbb-validate.sh [HLQ] [dirFonte]
#
# Exemplo:
#   ./zowe/dbb-validate.sh IBMUSER /u/ibmuser/projects/zopeneditor-sample
###############################################################################

set -e

###############################################################################
# Configuração — ajuste antes de executar
###############################################################################
HLQ="${1:-RAFAELT}"
SOURCE_DIR="${2:-/u/${HLQ,,}/projects/zopeneditor-sample}"
LOCAL_REPO="$(cd "$(dirname "$0")/.." && pwd)"
FILES_CMD="${FILES_CMD:-rse}"          # para z/OSMF use "files"
JOBS_CMD="${JOBS_CMD:-rse}"            # para z/OSMF use "zos-jobs"
SSHPROFILE="${SSHPROFILE:-}"           # ex: --rse-profile meuPerfil

###############################################################################
# Cores para saída (desabilitado se não for terminal)
###############################################################################
if [ -t 1 ]; then
    VERDE="\033[0;32m"
    VERMELHO="\033[0;31m"
    AMARELO="\033[0;33m"
    AZUL="\033[0;34m"
    RESET="\033[0m"
    NEGRITO="\033[1m"
else
    VERDE=""; VERMELHO=""; AMARELO=""; AZUL=""; RESET=""; NEGRITO=""
fi

###############################################################################
# Contadores de validação
###############################################################################
TOTAL=0
PASSOU=0
FALHOU=0
AVISOS=0

###############################################################################
# Funções auxiliares
###############################################################################
cabecalho() {
    echo ""
    echo "${AZUL}${NEGRITO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
    echo "${AZUL}${NEGRITO}  $1${RESET}"
    echo "${AZUL}${NEGRITO}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
}

verificar() {
    # Uso: verificar "descrição do teste" <código de saída 0=OK, 1=FALHOU, 2=AVISO>
    local desc="$1"
    local rc="$2"
    local detalhe="${3:-}"
    TOTAL=$((TOTAL + 1))
    if [ "$rc" -eq 0 ]; then
        echo "  ${VERDE}✔${RESET}  $desc"
        PASSOU=$((PASSOU + 1))
    elif [ "$rc" -eq 2 ]; then
        echo "  ${AMARELO}⚠${RESET}  $desc"
        [ -n "$detalhe" ] && echo "     ${AMARELO}↳ $detalhe${RESET}"
        AVISOS=$((AVISOS + 1))
        PASSOU=$((PASSOU + 1))
    else
        echo "  ${VERMELHO}✘${RESET}  $desc"
        [ -n "$detalhe" ] && echo "     ${VERMELHO}↳ $detalhe${RESET}"
        FALHOU=$((FALHOU + 1))
    fi
}

###############################################################################
# INÍCIO DA VALIDAÇÃO
###############################################################################
echo ""
echo "${NEGRITO}╔══════════════════════════════════════════════════════════════════════╗${RESET}"
echo "${NEGRITO}║   IBM DBB — Validação de Integração — zopeneditor-sample            ║${RESET}"
echo "${NEGRITO}╚══════════════════════════════════════════════════════════════════════╝${RESET}"
echo ""
echo "  HLQ              : ${HLQ}"
echo "  Repositório local: ${LOCAL_REPO}"
echo "  Diretório USS    : ${SOURCE_DIR}"
echo "  Perfil Zowe      : ${SSHPROFILE:-padrão}"
echo "  Data/Hora        : $(date '+%d/%m/%Y %H:%M:%S')"

###############################################################################
# 1. VERIFICAÇÃO DO AMBIENTE LOCAL
###############################################################################
cabecalho "1. AMBIENTE LOCAL"

# 1.1 DBB_HOME
if [ -n "$DBB_HOME" ] && [ -d "$DBB_HOME" ]; then
    verificar "DBB_HOME definido: $DBB_HOME" 0
else
    verificar "DBB_HOME não definido ou inválido" 1 "Defina: export DBB_HOME=/caminho/para/dbb"
fi

# 1.2 groovyz disponível
if [ -n "$DBB_HOME" ] && [ -f "$DBB_HOME/bin/groovyz" ]; then
    verificar "groovyz disponível em \$DBB_HOME/bin/groovyz" 0
else
    verificar "groovyz não encontrado" 2 "Necessário para executar scripts DBB Groovy"
fi

# 1.3 Zowe CLI
if command -v zowe >/dev/null 2>&1; then
    ZOWE_VER=$(zowe --version 2>/dev/null || echo "desconhecida")
    verificar "Zowe CLI instalado (versão: $ZOWE_VER)" 0
else
    verificar "Zowe CLI não encontrado no PATH" 1 "Instale: npm install -g @zowe/cli"
fi

# 1.4 Java
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -1)
    verificar "Java disponível: $JAVA_VER" 0
else
    verificar "Java não encontrado no PATH" 1 "Instale JDK 11 ou superior"
fi

###############################################################################
# 2. ESTRUTURA DO REPOSITÓRIO LOCAL
###############################################################################
cabecalho "2. ESTRUTURA DO REPOSITÓRIO LOCAL"

# Diretórios esperados
for DIR in COBOL PLI ASM COPYBOOK INCLUDES ASMCOPY REXX JCL application-conf groovy; do
    if [ -d "${LOCAL_REPO}/${DIR}" ]; then
        COUNT=$(ls "${LOCAL_REPO}/${DIR}" 2>/dev/null | wc -l | tr -d ' ')
        verificar "Diretório ${DIR}/ existe (${COUNT} arquivo(s))" 0
    else
        verificar "Diretório ${DIR}/ não encontrado" 1 "Esperado em ${LOCAL_REPO}/${DIR}"
    fi
done

# Arquivos de fonte esperados
for FONTE in COBOL/SAM1.cbl COBOL/SAM2.cbl COBOL/SAM1LIB.cbl PLI/PSAM1.pli PLI/PSAM2.pli ASM/ASAM1.asm REXX/RSAM1.rexx; do
    if [ -f "${LOCAL_REPO}/${FONTE}" ]; then
        verificar "Fonte ${FONTE} encontrado" 0
    else
        verificar "Fonte ${FONTE} não encontrado" 1
    fi
done

# Copybooks/includes críticos
for COPY in COPYBOOK/CUSTCOPY.cpy COPYBOOK/TRANREC.cpy INCLUDES/CUSTPLI.inc INCLUDES/BALSTATS.inc; do
    if [ -f "${LOCAL_REPO}/${COPY}" ]; then
        verificar "Copybook/Include ${COPY} encontrado" 0
    else
        verificar "Copybook/Include ${COPY} não encontrado" 1
    fi
done

# Arquivos de configuração
for CFG in build.properties dbb-app.yaml application-conf/application.properties \
           application-conf/Cobol.properties application-conf/PLI.properties \
           application-conf/Assembler.properties application-conf/REXX.properties \
           application-conf/file.properties application-conf/datasets-sandbox.properties; do
    if [ -f "${LOCAL_REPO}/${CFG}" ]; then
        verificar "Arquivo de configuração ${CFG} existe" 0
    else
        verificar "Arquivo de configuração ${CFG} não encontrado" 1
    fi
done

# Scripts Groovy de build
for SCRIPT in groovy/dbb-utilities.groovy groovy/dbb-cobol-build.groovy \
              groovy/dbb-pli-build.groovy groovy/dbb-asm-build.groovy \
              groovy/dbb-rexx-build.groovy groovy/dbb-full-build.groovy \
              groovy/dbb-sam-build.groovy; do
    if [ -f "${LOCAL_REPO}/${SCRIPT}" ]; then
        verificar "Script de build ${SCRIPT} existe" 0
    else
        verificar "Script de build ${SCRIPT} não encontrado" 2 "Pode ser necessário para execução do build"
    fi
done

###############################################################################
# 3. VERIFICAÇÃO DE CONECTIVIDADE ZOWE
###############################################################################
cabecalho "3. CONECTIVIDADE ZOWE"

if command -v zowe >/dev/null 2>&1; then
    # Testa listagem de datasets como proxy de conectividade
    if zowe $FILES_CMD list ds "${HLQ}.*" $SSHPROFILE >/dev/null 2>&1; then
        verificar "Conectividade Zowe com z/OS validada (HLQ: ${HLQ})" 0
    else
        verificar "Não foi possível conectar ao z/OS via Zowe" 2 \
            "Verifique: zowe profiles list --show-contents"
    fi
else
    verificar "Zowe CLI ausente — teste de conectividade ignorado" 2
fi

###############################################################################
# 4. DATASETS NO z/OS (pós-build)
###############################################################################
cabecalho "4. DATASETS NO z/OS (requer build anterior)"

if command -v zowe >/dev/null 2>&1; then
    for DS in "${HLQ}.SAMPLE.COBOL" "${HLQ}.SAMPLE.PLI" "${HLQ}.SAMPLE.ASM" \
              "${HLQ}.SAMPLE.OBJ" "${HLQ}.SAMPLE.LOAD" \
              "${HLQ}.SAMPLE.COPY" "${HLQ}.SAMPLE.PLINC" \
              "${HLQ}.SAMPLE.ASMCOPY" "${HLQ}.SAMPLE.REXX"; do
        if zowe $FILES_CMD list ds "$DS" $SSHPROFILE >/dev/null 2>&1; then
            verificar "Dataset ${DS} existe no z/OS" 0
        else
            verificar "Dataset ${DS} não encontrado" 2 "Execute o build para criar os datasets"
        fi
    done

    # Verifica membros críticos no LOAD
    for MEMBER in SAM1 SAM2 SAM1LIB PSAM1 PSAM2 ASAM1; do
        if zowe $FILES_CMD list am "${HLQ}.SAMPLE.LOAD($MEMBER)" $SSHPROFILE >/dev/null 2>&1; then
            verificar "Load module ${HLQ}.SAMPLE.LOAD(${MEMBER}) existe" 0
        else
            verificar "Load module ${HLQ}.SAMPLE.LOAD(${MEMBER}) não encontrado" 2 \
                "Execute o build correspondente"
        fi
    done
else
    verificar "Zowe CLI ausente — verificação de datasets ignorada" 2
fi

###############################################################################
# 5. ANÁLISE DOS LOGS DE BUILD
###############################################################################
cabecalho "5. LOGS DE BUILD"

LOGS_DIR="${LOCAL_REPO}/logs"
if [ -d "$LOGS_DIR" ]; then
    LOG_COUNT=$(ls "${LOGS_DIR}"/*.log 2>/dev/null | wc -l | tr -d ' ')
    verificar "Diretório de logs existe (${LOG_COUNT} arquivo(s))" 0

    # Verifica RCs nos logs
    if [ "$LOG_COUNT" -gt 0 ]; then
        RC_ERROS=0
        for LOG in "${LOGS_DIR}"/*.log; do
            if grep -q "failed\|FALHOU\|RC=8\|RC=12\|RC=16\|ABEND" "$LOG" 2>/dev/null; then
                echo "  ${VERMELHO}✘${RESET}  Erro detectado em: $(basename $LOG)"
                RC_ERROS=$((RC_ERROS + 1))
                FALHOU=$((FALHOU + 1))
                TOTAL=$((TOTAL + 1))
            fi
        done
        if [ "$RC_ERROS" -eq 0 ]; then
            verificar "Nenhum erro crítico detectado nos logs de build" 0
        fi
    fi

    # Verifica relatório de build completo
    if [ -f "${LOGS_DIR}/dbb-full-build-report.txt" ]; then
        verificar "Relatório de build completo encontrado" 0
        echo ""
        echo "${AZUL}  ── Conteúdo do último relatório de build: ─────────────────────────${RESET}"
        sed 's/^/  /' "${LOGS_DIR}/dbb-full-build-report.txt"
    else
        verificar "Relatório de build completo não encontrado" 2 \
            "Execute: groovyz groovy/dbb-full-build.groovy ${HLQ} ${SOURCE_DIR}"
    fi
else
    verificar "Diretório de logs não encontrado" 2 \
        "Será criado automaticamente durante o build"
fi

###############################################################################
# RELATÓRIO FINAL
###############################################################################
echo ""
echo "${NEGRITO}╔══════════════════════════════════════════════════════════════════════╗${RESET}"
echo "${NEGRITO}║   RELATÓRIO FINAL DE VALIDAÇÃO                                       ║${RESET}"
echo "${NEGRITO}╚══════════════════════════════════════════════════════════════════════╝${RESET}"
echo ""
echo "  Total de verificações : ${TOTAL}"
echo "  ${VERDE}Aprovadas              : ${PASSOU}${RESET}"
echo "  ${VERMELHO}Reprovadas             : ${FALHOU}${RESET}"
echo ""

if [ "$FALHOU" -eq 0 ]; then
    echo "  ${VERDE}${NEGRITO}✔  VALIDAÇÃO CONCLUÍDA COM SUCESSO!${RESET}"
    echo ""
    echo "  O repositório está corretamente configurado para o IBM DBB."
    echo "  Execute o build completo com:"
    echo ""
    echo "    ${AZUL}\$DBB_HOME/bin/groovyz groovy/dbb-full-build.groovy ${HLQ} ${SOURCE_DIR}${RESET}"
    SCRIPT_RC=0
else
    echo "  ${VERMELHO}${NEGRITO}✘  VALIDAÇÃO COM FALHAS — corrija os itens marcados acima.${RESET}"
    echo ""
    echo "  Consulte o arquivo README-DBB.md para instruções detalhadas."
    SCRIPT_RC=1
fi

echo ""
echo "  Data/Hora de conclusão: $(date '+%d/%m/%Y %H:%M:%S')"
echo ""

# Salva relatório em arquivo
REPORT_FILE="${LOCAL_REPO}/logs/dbb-validate-report.txt"
mkdir -p "${LOCAL_REPO}/logs"
{
    echo "IBM DBB — Relatório de Validação"
    echo "Data/Hora: $(date '+%d/%m/%Y %H:%M:%S')"
    echo "HLQ: ${HLQ} | Workspace: ${SOURCE_DIR}"
    echo "Total: ${TOTAL} | Aprovadas: ${PASSOU} | Reprovadas: ${FALHOU}"
    echo "Status: $([ "$FALHOU" -eq 0 ] && echo 'SUCESSO' || echo 'FALHOU')"
} > "$REPORT_FILE"

echo "  Relatório salvo em: ${REPORT_FILE}"
echo ""

exit $SCRIPT_RC
