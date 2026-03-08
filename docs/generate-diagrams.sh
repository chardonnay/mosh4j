#!/usr/bin/env bash
#
# Renders all Mermaid diagrams in docs/diagrams/ to PNG in docs/images/.
#
# Prerequisites:
#   npm install -g @mermaid-js/mermaid-cli
#
# Usage:
#   ./docs/generate-diagrams.sh            # render all diagrams
#   ./docs/generate-diagrams.sh <name>     # render a single diagram (without extension)
#
# Example:
#   ./docs/generate-diagrams.sh mosh4j-architecture

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIAGRAMS_DIR="${SCRIPT_DIR}/diagrams"
IMAGES_DIR="${SCRIPT_DIR}/images"

if ! command -v mmdc &>/dev/null; then
    echo "Error: mmdc (Mermaid CLI) is not installed."
    echo "Install it with:  npm install -g @mermaid-js/mermaid-cli"
    exit 1
fi

mkdir -p "${IMAGES_DIR}"

render_diagram() {
    local src="$1"
    local name
    name="$(basename "${src}" .mmd)"
    local dest="${IMAGES_DIR}/${name}.png"

    echo "Rendering ${src} -> ${dest}"
    mmdc -i "${src}" -o "${dest}" \
         -b transparent \
         -w 900 \
         -s 2
    echo "  Done: ${dest}"
}

if [[ $# -gt 0 ]]; then
    target="${DIAGRAMS_DIR}/$1.mmd"
    if [[ ! -f "${target}" ]]; then
        echo "Error: diagram not found: ${target}"
        exit 1
    fi
    render_diagram "${target}"
else
    found=0
    for src in "${DIAGRAMS_DIR}"/*.mmd; do
        [[ -f "${src}" ]] || continue
        render_diagram "${src}"
        found=1
    done
    if [[ ${found} -eq 0 ]]; then
        echo "No .mmd files found in ${DIAGRAMS_DIR}"
        exit 1
    fi
fi

echo ""
echo "All diagrams rendered successfully."
echo "Images are in: ${IMAGES_DIR}"
