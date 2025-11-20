#!/usr/bin/env bash

show_tree=false

# Parse arguments
for arg in "$@"; do
    case "$arg" in
        --tree)
            show_tree=true
            ;;
    esac
done

# --- Determine GitHub information ---
remote=$(git config --get remote.origin.url)
remote=${remote/git@github.com:/https://github.com/}
remote=${remote/.git/}

github_user=$(basename "$(dirname "$remote")")
github_repo=$(basename "$remote")
branch=$(git rev-parse --abbrev-ref HEAD)

raw_prefix="https://raw.githubusercontent.com/$github_user/$github_repo/refs/heads/$branch/"

# --- Gather non-ignored files ---
mapfile -t files < <(git ls-files && git ls-files --others --exclude-standard)
IFS=$'\n' sorted=($(sort <<<"${files[*]}"))
unset IFS

# --- Capture output into a variable ---
output=""

if ! $show_tree; then
    for file in "${sorted[@]}"; do
        output+="${raw_prefix}${file}"$'\n'
    done
else
    output+="Repository: $github_user/$github_repo (branch: $branch)"$'\n'
    output+="Raw prefix: $raw_prefix"$'\n\n'
    output+="."$'\n\n'

    declare -A printed_dirs

    for file in "${sorted[@]}"; do
        IFS='/' read -ra parts <<< "$file"
        path=""

        # Print intermediate directories only once
        for i in "${!parts[@]}"; do
            part="${parts[$i]}"
            path+="$part"

            if [[ -d "$path" && -z "${printed_dirs[$path]}" ]]; then
                printed_dirs[$path]=1

                depth=$(( $(grep -o "/" <<< "$path" | wc -l) ))
                indent=""
                for ((d=0; d<depth; d++)); do
                    indent+="│   "
                done
                output+="${indent}├── ${part}"$'\n'
            fi

            path+="/"
        done

        depth=$(( ${#parts[@]} - 1 ))
        indent=""
        for ((i=0; i<depth; i++)); do
            indent+="│   "
        done

        output+="${indent}└── ${parts[-1]}"$'\n'
        output+="${indent}    ↳ ${raw_prefix}${file}"$'\n'
    done
fi

# --- Copy to clipboard ---
if command -v pbcopy &>/dev/null; then
    printf "%s" "$output" | pbcopy
    echo "Output copied to clipboard (macOS pbcopy)."
elif command -v xclip &>/dev/null; then
    printf "%s" "$output" | xclip -selection clipboard
    echo "Output copied to clipboard (Linux xclip)."
elif command -v xsel &>/dev/null; then
    printf "%s" "$output" | xsel --clipboard --input
    echo "Output copied to clipboard (Linux xsel)."
elif command -v powershell.exe &>/dev/null; then
    # Windows Git Bash / MSYS / Cygwin / WSL
    printf "%s" "$output" | powershell.exe -NoProfile -Command "Set-Clipboard"
    echo "Output copied to clipboard (Windows PowerShell)."
fi
    printf "%s\n" "$output"
