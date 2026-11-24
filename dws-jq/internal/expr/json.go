package expr

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"math/big"
	"strconv"
	"strings"
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

// Encode serializes a gojq value the way jq prints it: compact, keys sorted,
// no HTML escaping, NaN as null and ±Infinity as ±1.7976931348623157e+308.
func Encode(v any) ([]byte, error) {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(finite(v)); err != nil {
		return nil, fmt.Errorf("encode JSON: %w", err)
	}
	return bytes.TrimSuffix(buf.Bytes(), []byte("\n")), nil
}

// finite returns v with every non-finite float replaced; v is not modified.
func finite(v any) any {
	switch v := v.(type) {
	case float64:
		switch {
		case math.IsNaN(v):
			return nil
		case math.IsInf(v, 1):
			return math.MaxFloat64
		case math.IsInf(v, -1):
			return -math.MaxFloat64
		}
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, item := range v {
			out[key] = finite(item)
		}
		return out
	case []any:
		out := make([]any, len(v))
		for i, item := range v {
			out[i] = finite(item)
		}
		return out
	}
	return v
}
