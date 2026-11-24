package expr

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"strconv"
	"strings"

	"github.com/itchyny/gojq"
)

// DecodeJSON parses exactly one JSON document into gojq's value model:
// integers become int (or *big.Int beyond the int range), other numbers
// float64, so large integers keep full precision.
func DecodeJSON(data []byte) (any, error) {
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber()
	var v any
	if err := dec.Decode(&v); err != nil {
		return nil, fmt.Errorf("decode JSON: %w", err)
	}
	if _, err := dec.Token(); !errors.Is(err, io.EOF) {
		return nil, errors.New("decode JSON: unexpected data after the top-level value")
	}
	return normalize(v)
}

func normalize(v any) (any, error) {
	switch v := v.(type) {
	case json.Number:
		return normalizeNumber(v)
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, item := range v {
			n, err := normalize(item)
			if err != nil {
				return nil, err
			}
			out[key] = n
		}
		return out, nil
	case []any:
		out := make([]any, len(v))
		for i, item := range v {
			n, err := normalize(item)
			if err != nil {
				return nil, err
			}
			out[i] = n
		}
		return out, nil
	}
	return v, nil
}

func normalizeNumber(n json.Number) (any, error) {
	text := n.String()
	if !strings.ContainsAny(text, ".eE") {
		if i, ok := new(big.Int).SetString(text, 10); ok {
			if i.IsInt64() && int64(int(i.Int64())) == i.Int64() {
				return int(i.Int64()), nil
			}
			return i, nil
		}
	}
	f, err := strconv.ParseFloat(text, 64)
	if err != nil && !errors.Is(err, strconv.ErrRange) {
		return nil, fmt.Errorf("decode JSON number %q: %w", text, err)
	}
	return f, nil
}

// Encode serializes a gojq value exactly as jq prints it (compact, sorted
// keys, NaN as null, ±Infinity clamped, no HTML escaping), by delegating to
// gojq.Marshal. gojq.Marshal panics on a value outside gojq's value model;
// that is reported as an error instead.
func Encode(v any) (out []byte, err error) {
	defer func() {
		if r := recover(); r != nil {
			out, err = nil, fmt.Errorf("encode JSON: %v", r)
		}
	}()
	return gojq.Marshal(v)
}
