#!/bin/bash

file_path="IoTDevice.jar"
output_file="attestation.txt"

# Get the byte size of the file
byte_size=$(wc -c < "$file_path")

# Write the byte size to the output file
echo "$byte_size" > "$output_file"

echo "Size of IoTDevice.jar is written into attestation.txt"
