#!/usr/bin/env bash

show_tree=false
show_java_packages=false

# Parse arguments
for arg in "$@"; do
    case "$arg" in
        --tree)
            show_tree=true
            ;;
        --java-packages)
            show_java_packages=true
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

output=""

# --- Collapsed Java package tree ---
if $show_java_packages; then
    roots=()
    [[ -d src/main/java ]] && roots+=(src/main/java)
    [[ -d src/test/java ]] && roots+=(src/test/java)

    if [[ ${#roots[@]} -eq 0 ]]; then
        echo "No Java source roots found."
        exit 1
    fi

    declare -A packages

    for root in "${roots[@]}"; do
        while IFS= read -r -d '' dir; do
            pkg="${dir#$root/}"
            [[ "$pkg" == "$dir" ]] && continue
            pkg="${pkg//\//.}"
            packages["$pkg"]=1
        done < <(find "$root" -type d -print0)
    done

    # Find common base package
    common=""
    for pkg in "${!packages[@]}"; do
        if [[ -z "$common" ]]; then
            common="$pkg"
        else
            while [[ "${pkg#$common}" == "$pkg" ]]; do
                common="${common%.*}"
                [[ -z "$common" ]] && break
            done
        fi
    done

    output+="$common"$'\n'

    declare -A printed

    for pkg in $(printf "%s\n" "${!packages[@]}" | sort); do
        [[ "$pkg" != "$common"* ]] && continue

        rest="${pkg#$common.}"
        [[ "$rest" == "$pkg" ]] && continue

        IFS='.' read -ra parts <<< "$rest"
        path=""

        for part in "${parts[@]}"; do
            path+="$part"
            if [[ -z "${printed[$path]}" ]]; then
                printed["$path"]=1
                depth=$(( $(grep -o "\." <<< "$path" | wc -l) ))
                indent=""
                for ((i=0; i<depth; i++)); do indent+="│   "; done
                output+="${indent}├── $part"$'\n'
            fi
            path+="."
        done
    done

# --- Default modes ---
elif ! $show_tree; then
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

        for i in "${!parts[@]}"; do
            part="${parts[$i]}"
            path+="$part"

            if [[ -d "$path" && -z "${printed_dirs[$path]}" ]]; then
                printed_dirs[$path]=1
                depth=$(( $(grep -o "/" <<< "$path" | wc -l) ))
                indent=""
                for ((d=0; d<depth; d++)); do indent+="│   "; done
                output+="${indent}├── ${part}"$'\n'
            fi

            path+="/"
        done

        depth=$(( ${#parts[@]} - 1 ))
        indent=""
        for ((i=0; i<depth; i++)); do indent+="│   "; done

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
    printf "%s" "$output" | powershell.exe -NoProfile -Command "Set-Clipboard"
    echo "Output copied to clipboard (Windows PowerShell)."
fi

printf "%s\n" "$output"
