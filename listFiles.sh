#!/usr/bin/env bash

show_tree=false
show_tree_names=false
show_java_packages=false
show_raw_files=false
show_last_commit=false

# Function to display help
show_help() {
    echo "Usage: $(basename "$0") [options]"
    echo ""
    echo "Options:"
    echo "  --tree            Show directory tree with raw GitHub links"
    echo "  --tree-names      Show directory tree with filenames only (no links)"
    echo "  --java-packages   Show collapsed Java package structure"
    echo "  --raw-files       List full raw GitHub URLs"
    echo "  --last-commit     List URLs for files changed in the last commit (with status)"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Git Status Codes (used in --last-commit):"
    echo "  [A] Added      [M] Modified"
    echo "  [D] Deleted    [R] Renamed"
}

# No options provided
if [ $# -eq 0 ]; then
    show_help
    exit 0
fi

# Parse arguments
for arg in "$@"; do
    case "$arg" in
        --tree) show_tree=true ;;
        --tree-names) show_tree_names=true ;;
        --java-packages) show_java_packages=true ;;
        --raw-files) show_raw_files=true ;;
        --last-commit) show_last_commit=true ;;
        -h|--help) show_help; exit 0 ;;
        *) echo "Unknown option: $arg"; show_help; exit 1 ;;
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

# --- Gather files based on mode ---
declare -A file_statuses
if $show_last_commit; then
    # Get status and filename (A, M, D, etc.)
    mapfile -t raw_git_data < <(git show --name-status --format= HEAD)
    files=()
    for line in "${raw_git_data[@]}"; do
        [[ -z "$line" ]] && continue
        status=$(echo "$line" | awk '{print $1}')
        fname=$(echo "$line" | awk '{print $2}')
        files+=("$fname")
        file_statuses["$fname"]="$status"
    done
else
    mapfile -t files < <(git ls-files && git ls-files --others --exclude-standard)
fi

IFS=$'\n' sorted=($(sort <<<"${files[*]}"))
unset IFS

output=""

# --- Logic for --last-commit and --raw-files ---
if $show_last_commit || $show_raw_files; then
    if $show_last_commit; then
        output+="LAST COMMIT CHANGES (Legend: [A]dded, [M]odified, [D]eleted, [R]enamed)"$'\n'
        output+="----------------------------------------------------------------------"$'\n'
    fi

    for file in "${sorted[@]}"; do
        [[ -z "$file" ]] && continue
        prefix=""
        if [[ -n "${file_statuses[$file]}" ]]; then
            prefix="[${file_statuses[$file]}] "
        fi
        output+="${prefix}${raw_prefix}${file}"$'\n'
    done

# --- Logic for --java-packages ---
elif $show_java_packages; then
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

    common=""
    for pkg in "${!packages[@]}"; do
        if [[ -z "$common" ]]; then common="$pkg";
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

# --- Tree Modes (Standard or Names Only) ---
elif $show_tree || $show_tree_names; then
    output+="Repository: $github_user/$github_repo (branch: $branch)"$'\n'
    output+="Raw prefix: $raw_prefix"$'\n\n'
    output+="."$'\n\n'

    declare -A printed_dirs
    for file in "${sorted[@]}"; do
        [[ -z "$file" ]] && continue
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
        if ! $show_tree_names; then
            output+="${indent}    ↳ ${raw_prefix}${file}"$'\n'
        fi
    done
fi

# --- Copy to clipboard ---
if [[ -n "$output" ]]; then
    if command -v pbcopy &>/dev/null; then printf "%s" "$output" | pbcopy
    elif command -v xclip &>/dev/null; then printf "%s" "$output" | xclip -selection clipboard
    elif command -v powershell.exe &>/dev/null; then printf "%s" "$output" | powershell.exe -NoProfile -Command "Set-Clipboard"
    fi
    printf "%s\n" "$output"
fi