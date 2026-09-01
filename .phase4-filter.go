package main

import (
	"io"
	"os"
	"strings"
)

func main() {
	input, err := io.ReadAll(os.Stdin)
	if err != nil {
		panic(err)
	}

	var kept []string
	for _, document := range strings.Split(string(input), "\n---\n") {
		if !strings.Contains(document, "# Source: dws/charts/dapr/") {
			kept = append(kept, document)
		}
	}

	if _, err := os.Stdout.WriteString(strings.Join(kept, "\n---\n")); err != nil {
		panic(err)
	}
}
