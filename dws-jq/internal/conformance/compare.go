package conformance

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"math/big"
	"strconv"
	"strings"
)

// jsonEqual reports whether two JSON documents hold the same value. Object
// key order does not matter and numbers compare by value, so 10 equals 10.0.
// Two integers compare exactly (beyond 2^53 too); any other pair of numbers
// compares as float64.
func jsonEqual(a, b []byte) (bool, error) {
	va, err := decodeTree(a)
	if err != nil {
		return false, err
	}
	vb, err := decodeTree(b)
	if err != nil {
		return false, err
	}
	return treeEqual(va, vb), nil
}

// decodeTree decodes one JSON document keeping numbers as json.Number. An
// empty document is null.
func decodeTree(doc []byte) (any, error) {
	if len(bytes.TrimSpace(doc)) == 0 {
		return nil, nil
	}
	dec := json.NewDecoder(bytes.NewReader(doc))
	dec.UseNumber()
	var v any
	if err := dec.Decode(&v); err != nil {
		return nil, err
	}
	if _, err := dec.Token(); !errors.Is(err, io.EOF) {
		return nil, errors.New("unexpected data after the top-level value")
	}
	return v, nil
}

func treeEqual(a, b any) bool {
	switch a := a.(type) {
	case json.Number:
		bn, ok := b.(json.Number)
		return ok && numberEqual(a, bn)
	case map[string]any:
		bm, ok := b.(map[string]any)
		if !ok || len(a) != len(bm) {
			return false
		}
		for key, av := range a {
			bv, present := bm[key]
			if !present || !treeEqual(av, bv) {
				return false
			}
		}
		return true
	case []any:
		bs, ok := b.([]any)
		if !ok || len(a) != len(bs) {
			return false
		}
		for i := range a {
			if !treeEqual(a[i], bs[i]) {
				return false
			}
		}
		return true
	}
	return a == b
}

func numberEqual(a, b json.Number) bool {
	ai, aInt := integer(a)
	bi, bInt := integer(b)
	if aInt && bInt {
		return ai.Cmp(bi) == 0
	}
	af, errA := strconv.ParseFloat(a.String(), 64)
	bf, errB := strconv.ParseFloat(b.String(), 64)
	return errA == nil && errB == nil && af == bf
}

// integer parses n as an exact integer when it is written without a fraction
// or an exponent.
func integer(n json.Number) (*big.Int, bool) {
	if strings.ContainsAny(n.String(), ".eE") {
		return nil, false
	}
	return new(big.Int).SetString(n.String(), 10)
}
