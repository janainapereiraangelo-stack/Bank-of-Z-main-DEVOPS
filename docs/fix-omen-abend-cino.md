# Correção Abend 4038 — Transação OMEN / CICSAOR1

## Resumo

| Campo              | Valor                          |
|--------------------|-------------------------------|
| **JOBID**          | STC02872                      |
| **Região CICS**    | CICSAOR1                      |
| **Transação**      | OMEN                          |
| **Programa**       | BNKMENU                       |
| **Código Abend**   | 4038 (IOERR)                  |
| **Data**           | 24/10/2026 17:14:56 e 17:15:33|
| **Usuário**        | RAFAELT (terminais 0189/0190) |

## Causa Raiz

A TDQUEUE `CINO` (fila de saída Transient Data do CICS) estava **fechada** na região CICSAOR1.
O programa `BNKMENU` tentou abri-la via `SET TDQUEUE(CINO) OPENSTATUS(OPEN)` e recebeu:

```
RESP(IOERR) RESP2(14)
```

`RESP2(14)` = `SYSIDERR` — dataset VSAM associado à TDQUEUE não encontrado no catálogo.
Isso levou ao abend código `4038` (`IOERR`) registrado pela mensagem `DFHAC2236`.

## Evidências dos Logs (MSGUSR - DD 106)

```
DFHAP1900 10/24/2026 17:14:56 CICSAOR1 MVS10189 RAFAELT OMEN
          SET TDQUEUE(CINO) OPENSTATUS(OPEN) RESP(IOERR) RESP2(14).

DFHAC2236 10/24/2026 17:14:56 CICSAOR1
          Transaction OMEN transaction number 69904 abend 4038
          in program BNKMENU term 0189.
```

## Ações Realizadas

### ✅ Sessões de Debug — CONCLUÍDO
- Os terminais 0189 e 0190 do usuário RAFAELT foram encerrados pelo sistema
  (`DFHZC3462` — `DFHZC6966`).
- O arquivo EQADPFMB contém apenas 1 perfil ativo (`JCMARCA`) — sem perfis
  pendentes de RAFAELT.

### ⚠️ TDQUEUE CINO — REQUER AÇÃO DO ADMINISTRADOR CICS

O usuário RAFAELT **não possui autoridade RACF UPDATE** no recurso:

```
MVS.MODIFY.STC.CICSAOR1.CICSAOR1  CL(OPERCMDS)
ACCESS INTENT(UPDATE)  ACCESS ALLOWED(READ)
```

**O administrador CICS deve executar o comando abaixo no console MVS ou via CEMT:**

```
F CICSAOR1,CEMT S TDQUEUE(CINO) OPEN
```

Ou submeter o JCL abaixo com um usuário que possua autoridade `UPDATE` em OPERCMDS:

```jcl
//FIXCINO  JOB (ACCT),'FIX CINO TDQUEUE',CLASS=A,MSGCLASS=H,
//         MSGLEVEL=(1,1),NOTIFY=&SYSUID
//*---------------------------------------------------------*
//* CORRECAO: ABRIR TDQUEUE CINO NA REGIAO CICSAOR1         *
//* ABEND 4038 TRANSACAO OMEN / PROGRAMA BNKMENU            *
//*---------------------------------------------------------*
//STEP1    EXEC PGM=IKJEFT01,REGION=0M
//SYSTSPRT DD SYSOUT=*
//SYSTSIN  DD *
  CONSOLE
  F CICSAOR1,CEMT S TDQUEUE(CINO) OPEN
  F CICSAOR1,CEMT I TDQUEUE(CINO)
  END
/*
```

### Verificação Pós-Correção

Após executar o comando acima, confirmar que a TDQUEUE está aberta:

```
F CICSAOR1,CEMT I TDQUEUE(CINO)
```

O retorno esperado é:
```
STATUS(  OPEN ) ...
```

Em seguida, solicitar que o usuário RAFAELT re-execute a transação `OMEN` e confirmar
que não há mais `DFHAC2236` com abend `4038` no MSGUSR.

## Prevenção

1. **Verificar por que a TDQUEUE CINO foi fechada** — checar se houve falha no dataset
   VSAM associado ou se alguém executou `CEMT S TDQUEUE(CINO) CLOSE` intencionalmente.
2. **Rever o `DEBUGTOOL=NO`** no SIT da região CICSAOR1 — com este parâmetro ativo,
   sessões do z/OS Debugger que terminam abruptamente podem deixar o enclave em estado
   inconsistente (`CEE2529S`), causando falhas em cascata.
3. **Considerar definir o dataset VSAM de CINO como `RECOVERABLE`** no CSD para garantir
   que a fila seja reaberta automaticamente após falha.
