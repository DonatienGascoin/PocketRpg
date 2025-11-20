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

# --- Default: raw URLs only ---
if ! $show_tree; then
    for file in "${sorted[@]}"; do
        echo "${raw_prefix}${file}"
    done
    exit 0
fi

# --- Condensed tree mode ---
echo "Repository: $github_user/$github_repo (branch: $branch)"
echo "Raw prefix: $raw_prefix"
echo
echo "."
echo

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

            # Print directory line
            depth=$(( $(grep -o "/" <<< "$path" | wc -l) ))
            indent=""
            for ((d=0; d<depth; d++)); do
                indent+="│   "
            done
            echo "${indent}├── ${part}"
        fi

        path+="/"
    done

    # Now print the file
    depth=$(( ${#parts[@]} - 1 ))
    indent=""
    for ((i=0; i<depth; i++)); do
        indent+="│   "
    done

    echo "${indent}└── ${parts[-1]}"
    echo "${indent}    ↳ ${raw_prefix}${file}"
done
