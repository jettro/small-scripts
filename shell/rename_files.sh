#!/bin/bash

# Navigate to the directory (optional, uncomment and set the path if needed)
cd /Users/jettrocoenradie/Downloads/yvonne

# Loop through all files in the directory
for file in *; do
    # Skip directories and non-regular files
    if [ ! -f "$file" ]; then
        continue
    fi

    # Normalize the filename by removing '(1)', replacing spaces, and cleaning up
    newname="${file//(1)/}"                # Remove '(1)'
    newname="${newname// /-}"              # Replace spaces with '_'
    newname="${newname//[^a-zA-Z0-9_.-]/}" # Remove special characters except '.', '_', and '-'
    newname="$(echo $newname | tr '[:upper:]' '[:lower:]')" # Convert to lowercase

    # Separate the base from the extension
    base="${newname%.*}"                   # Everything before the last dot
    ext="${newname##*.}"                   # Everything after the last dot

    # Remove any non-alphanumeric characters right before the dot
    base="${base%-}"

    # Reassemble the filename
    newname="${base}.${ext}"

    # Rename the file if new and old names are not the same
    if [[ "$file" != "$newname" ]]; then
        mv "$file" "$newname"
        echo "Renamed $file to $newname"
    fi
done

