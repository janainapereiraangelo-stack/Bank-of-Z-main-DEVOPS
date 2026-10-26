#!/usr/bin/env python3
"""
Bank of Z — DevOps Pipeline Configuration Script
=================================================

Gera, valida e exibe a configuração completa de todos os componentes
da esteira DevOps do Bank of Z a partir do arquivo config.yaml.

Componentes cobertos:
  - Global / Aplicação / Sandbox
  - Java / Python / ZOAU
  - DBB (Dependency Based Build / zBuilder)
  - ZCodeScan (análise estática)
  - Wazi Deploy (geração e implantação)
  - CICS / IMS / DB2
  - z/OS Connect (API)
  - Frontend Liberty Server
  - Debug (IBM Z Open Debug)
  - Zowe CLI

Uso:
    python devops_config.py [--config CONFIG_FILE] [--section SECTION] [--output {text,json,yaml}]

Exemplos:
    python devops_config.py
    python devops_config.py --section dbb
    python devops_config.py --output json > pipeline-config.json
    CONFIG_FILE=/caminho/config.yaml python devops_config.py
"""

import argparse
import json
import os
import re
import sys
from copy import deepcopy
from dataclasses import dataclass, field
from typing import Any

# ---------------------------------------------------------------------------
# Dependências opcionais
# ---------------------------------------------------------------------------
try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

try:
    from jinja2 import Template
    HAS_JINJA2 = True
except ImportError:
    HAS_JINJA2 = False

# ---------------------------------------------------------------------------
# Constantes
# ---------------------------------------------------------------------------
DEFAULT_CONFIG_FILE = os.path.join(
    os.path.dirname(__file__), "..", ".setup", "config", "config.yaml"
)

ENV_PATTERN = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")

# Ordem de exibição das seções da esteira
PIPELINE_SECTIONS = [
    ("global",      "🌐  Global / z/OS System"),
    ("app",         "📦  Aplicação"),
    ("sandbox",     "🗂️   Sandbox"),
    ("java",        "☕  Java"),
    ("python",      "🐍  Python"),
    ("zoau",        "🔧  ZOAU (z/OS Open Automation Utilities)"),
    ("zconfig",     "⚙️   ZConfig / CICS Resource Builder"),
    ("dbb",         "🏗️   DBB (Dependency Based Build)"),
    ("zbuilder",    "🔨  zBuilder"),
    ("zcodescan",   "🔍  ZCodeScan (Análise Estática)"),
    ("wazideploy",  "🚀  Wazi Deploy"),
    ("zosconnect",  "🔌  z/OS Connect"),
    ("frontend",    "🖥️   Frontend Liberty Server"),
    ("cics",        "🔄  CICS"),
    ("ims",         "📂  IMS"),
    ("db2",         "🗄️   DB2"),
    ("debug",       "🐛  Debug (IBM Z Open Debug)"),
    ("zowe",        "🔗  Zowe CLI"),
    ("repositories","📁  Repositórios Git"),
]

# Campos sensíveis que não devem ser exibidos em texto claro
SENSITIVE_FIELDS = {"password", "secret", "token", "pat", "key"}


# ---------------------------------------------------------------------------
# Resolução de configuração
# ---------------------------------------------------------------------------

def expand_env_vars(value: str) -> str:
    """Expande variáveis de ambiente no formato ${VAR}."""
    if not isinstance(value, str):
        return value

    def _repl(match: re.Match) -> str:
        return os.environ.get(match.group(1), f"${{{match.group(1)}}}")

    return ENV_PATTERN.sub(_repl, value)


def _render_node(node: Any, context: dict, changed_flag: list) -> Any:
    """Renderiza recursivamente um nó YAML resolvendo expressões Jinja2 e env vars."""
    if isinstance(node, dict):
        return {k: _render_node(v, context, changed_flag) for k, v in node.items()}
    if isinstance(node, list):
        return [_render_node(v, context, changed_flag) for v in node]
    if isinstance(node, str):
        rendered = node
        if "{{" in rendered and HAS_JINJA2:
            rendered = Template(rendered).render(**context)
        rendered = expand_env_vars(rendered)
        if rendered != node:
            changed_flag.append(True)
        return rendered
    return node


def render_config(data: dict) -> dict:
    """Resolve todas as referências Jinja2 e variáveis de ambiente (até 20 passes)."""
    result = deepcopy(data)
    for _ in range(20):
        changed: list = []
        result = _render_node(result, result, changed)
        if not changed:
            break
    return result


def load_config(config_file: str) -> dict:
    """Carrega o arquivo YAML de configuração."""
    if not HAS_YAML:
        print("[ERRO] Biblioteca 'pyyaml' não instalada. Execute: pip install pyyaml", file=sys.stderr)
        sys.exit(1)
    with open(config_file, "r", encoding="utf-8") as fh:
        return yaml.safe_load(fh)


# ---------------------------------------------------------------------------
# Componentes da esteira
# ---------------------------------------------------------------------------

@dataclass
class PipelineComponent:
    """Representa um componente da esteira DevOps com sua configuração."""
    name: str
    label: str
    config: dict = field(default_factory=dict)
    validation_errors: list = field(default_factory=list)
    validation_warnings: list = field(default_factory=list)

    @property
    def is_valid(self) -> bool:
        return len(self.validation_errors) == 0


# ---------------------------------------------------------------------------
# Validações por componente
# ---------------------------------------------------------------------------

def _check_path_key(component: PipelineComponent, key: str, label: str) -> None:
    """Emite aviso se um caminho de diretório contém variável não resolvida."""
    val = component.config.get(key, "")
    if isinstance(val, str) and "${" in val:
        component.validation_warnings.append(
            f"'{key}' contém variável não resolvida: {val}"
        )


def validate_dbb(comp: PipelineComponent) -> None:
    for key in ("dbb_home", "dbb_cwd", "dbb_app_conf"):
        _check_path_key(comp, key, key)


def validate_zcodescan(comp: PipelineComponent) -> None:
    for key in ("zcodescan_home", "src_dir", "rule_file"):
        _check_path_key(comp, key, key)
    max_rc = comp.config.get("max_rc")
    if max_rc is not None and int(max_rc) > 8:
        comp.validation_warnings.append(f"'max_rc' = {max_rc} é permissivo (>8); considere reduzir.")


def validate_wazideploy(comp: PipelineComponent) -> None:
    for key in ("wazideploy_home", "deployment_method", "deployment_envfile"):
        _check_path_key(comp, key, key)


def validate_cics(comp: PipelineComponent) -> None:
    cmci_port = comp.config.get("cmci_port")
    if cmci_port and not (1 <= int(str(cmci_port)) <= 65535):
        comp.validation_errors.append(f"'cmci_port' inválido: {cmci_port}")


def validate_db2(comp: PipelineComponent) -> None:
    ssid = comp.config.get("ssid", "")
    if ssid and len(str(ssid)) > 4:
        comp.validation_warnings.append(f"'ssid' ('{ssid}') excede 4 caracteres — verifique o SSID DB2.")


VALIDATORS = {
    "dbb": validate_dbb,
    "zcodescan": validate_zcodescan,
    "wazideploy": validate_wazideploy,
    "cics": validate_cics,
    "db2": validate_db2,
}


def build_components(resolved_config: dict) -> list[PipelineComponent]:
    """Constrói e valida os componentes da esteira a partir da configuração resolvida."""
    components = []
    for section_key, section_label in PIPELINE_SECTIONS:
        raw = resolved_config.get(section_key, {})
        # Normaliza: seções que não são dicts (ex.: scalars) viram dict vazio
        if not isinstance(raw, dict):
            raw = {}
        comp = PipelineComponent(name=section_key, label=section_label, config=raw)
        validator = VALIDATORS.get(section_key)
        if validator:
            validator(comp)
        components.append(comp)
    return components


# ---------------------------------------------------------------------------
# Formatação / Saída
# ---------------------------------------------------------------------------

CYAN  = "\033[96m"
GREEN = "\033[92m"
YELLOW= "\033[93m"
RED   = "\033[91m"
BOLD  = "\033[1m"
RESET = "\033[0m"


def _mask(key: str, value: Any) -> str:
    """Mascara valores sensíveis."""
    if isinstance(value, str) and any(s in key.lower() for s in SENSITIVE_FIELDS):
        return "****"
    return str(value)


def _flat_rows(cfg: dict, prefix: str = "") -> list[tuple[str, str]]:
    """Achata um dict aninhado em lista de (chave, valor)."""
    rows = []
    for k, v in cfg.items():
        full_key = f"{prefix}.{k}" if prefix else k
        if isinstance(v, dict):
            rows.extend(_flat_rows(v, prefix=full_key))
        elif isinstance(v, list):
            for i, item in enumerate(v):
                if isinstance(item, dict):
                    rows.extend(_flat_rows(item, prefix=f"{full_key}[{i}]"))
                else:
                    rows.append((f"{full_key}[{i}]", _mask(k, item)))
        else:
            rows.append((full_key, _mask(k, v)))
    return rows


def print_text(components: list[PipelineComponent], filter_section: str | None = None) -> None:
    """Exibe a configuração em formato de texto legível."""
    print()
    print(f"{BOLD}{'='*72}{RESET}")
    print(f"{BOLD}   Bank of Z — Configuração da Esteira DevOps{RESET}")
    print(f"{BOLD}{'='*72}{RESET}")

    for comp in components:
        if filter_section and comp.name != filter_section:
            continue

        status_icon = f"{GREEN}✔{RESET}" if comp.is_valid else f"{RED}✖{RESET}"
        warn_icon   = f" {YELLOW}⚠ {len(comp.validation_warnings)} aviso(s){RESET}" if comp.validation_warnings else ""
        print()
        print(f"{CYAN}{BOLD}{comp.label}{RESET}  {status_icon}{warn_icon}")
        print(f"{CYAN}{'─'*60}{RESET}")

        rows = _flat_rows(comp.config)
        if rows:
            max_key = max(len(r[0]) for r in rows)
            for k, v in rows:
                print(f"  {k:<{max_key}}  {v}")
        else:
            print("  (sem configuração)")

        for err in comp.validation_errors:
            print(f"  {RED}[ERRO]    {err}{RESET}")
        for warn in comp.validation_warnings:
            print(f"  {YELLOW}[AVISO]   {warn}{RESET}")

    print()
    print(f"{BOLD}{'='*72}{RESET}")
    _print_summary(components)
    print()


def _print_summary(components: list[PipelineComponent]) -> None:
    total  = len(components)
    errors = sum(1 for c in components if not c.is_valid)
    warns  = sum(len(c.validation_warnings) for c in components)

    color = GREEN if errors == 0 else RED
    print(f"\n{BOLD}  Resumo{RESET}")
    print(f"  Componentes verificados : {total}")
    print(f"  {color}Erros de validação      : {errors}{RESET}")
    print(f"  {YELLOW}Avisos                  : {warns}{RESET}")

    if errors == 0:
        print(f"\n  {GREEN}{BOLD}✔  Configuração válida — esteira pronta para execução.{RESET}")
    else:
        print(f"\n  {RED}{BOLD}✖  Corrija os erros antes de executar a esteira.{RESET}")


def print_json(components: list[PipelineComponent], filter_section: str | None = None) -> None:
    """Serializa a configuração como JSON."""
    output = {}
    for comp in components:
        if filter_section and comp.name != filter_section:
            continue
        output[comp.name] = {
            "label": comp.label,
            "config": comp.config,
            "validation": {
                "valid": comp.is_valid,
                "errors": comp.validation_errors,
                "warnings": comp.validation_warnings,
            },
        }
    print(json.dumps(output, indent=2, default=str, ensure_ascii=False))


def print_yaml_output(components: list[PipelineComponent], filter_section: str | None = None) -> None:
    """Serializa a configuração como YAML."""
    if not HAS_YAML:
        print("[ERRO] pyyaml não instalado.", file=sys.stderr)
        sys.exit(1)
    output = {}
    for comp in components:
        if filter_section and comp.name != filter_section:
            continue
        output[comp.name] = {
            "label": comp.label,
            "config": comp.config,
            "validation": {
                "valid": comp.is_valid,
                "errors": comp.validation_errors,
                "warnings": comp.validation_warnings,
            },
        }
    print(yaml.dump(output, allow_unicode=True, default_flow_style=False, sort_keys=False))


# ---------------------------------------------------------------------------
# Pipeline stages summary
# ---------------------------------------------------------------------------

PIPELINE_STAGES = [
    ("1. Clone",          "clone-repository-to-uss",   "git clone via gitClone.sh para USS"),
    ("2. ZCodeScan",      "run-zcodescan",              "Análise estática com IBM TAZ/ZCodeScan"),
    ("3. DBB Build",      "run-dbb-build",              "Compilação incremental (impact) ou completa (full)"),
    ("4. Wazi Deploy",    "run-wazi-deploy",            "Geração do plano de deploy + implantação em z/OS"),
    ("5. Verification",  "run-verification-tests",     "Testes de verificação pós-instalação"),
]


def print_pipeline_stages() -> None:
    print(f"\n{BOLD}  Estágios da Esteira GitHub Actions (Pipeline.yml){RESET}")
    print(f"  {'─'*60}")
    for stage, job, desc in PIPELINE_STAGES:
        print(f"  {CYAN}{stage:<18}{RESET}  job: {job:<30}  {desc}")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Bank of Z — Configuração da Esteira DevOps",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--config",
        default=os.environ.get("CONFIG_FILE", DEFAULT_CONFIG_FILE),
        help="Caminho para o arquivo config.yaml (padrão: .setup/config/config.yaml)",
    )
    parser.add_argument(
        "--section",
        default=None,
        choices=[s for s, _ in PIPELINE_SECTIONS],
        help="Exibe somente a seção especificada",
    )
    parser.add_argument(
        "--output",
        default="text",
        choices=["text", "json", "yaml"],
        help="Formato de saída (padrão: text)",
    )
    parser.add_argument(
        "--no-stages",
        action="store_true",
        help="Omite o resumo dos estágios da esteira",
    )
    parser.add_argument(
        "--list-sections",
        action="store_true",
        help="Lista todas as seções disponíveis e encerra",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.list_sections:
        print("\nSeções disponíveis:\n")
        for key, label in PIPELINE_SECTIONS:
            print(f"  {key:<14}  {label}")
        print()
        sys.exit(0)

    config_path = os.path.abspath(args.config)
    if not os.path.isfile(config_path):
        print(f"[ERRO] Arquivo de configuração não encontrado: {config_path}", file=sys.stderr)
        sys.exit(1)

    raw_config = load_config(config_path)
    resolved   = render_config(raw_config)
    components = build_components(resolved)

    if args.output == "json":
        print_json(components, filter_section=args.section)
    elif args.output == "yaml":
        print_yaml_output(components, filter_section=args.section)
    else:
        print_text(components, filter_section=args.section)
        if not args.no_stages and args.section is None:
            print_pipeline_stages()
            print()


if __name__ == "__main__":
    main()
