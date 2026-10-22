package basicauth

import (
	"encoding/base64"

	"golang.org/x/text/unicode/norm"
)

// HeaderValue returns the Authorization header value for this credential, as RFC 7617 describes:
// "Basic " followed by the padded standard base64 of the user-pass, which is the username, a
// colon and the password, each part in Unicode NFC and encoded as UTF-8 (the only charset the
// RFC defines). Go strings from encoding/json are already UTF-8, and a ':' in the password is
// kept verbatim.
//
// Full PRECIS profiles (width mapping, disallowed code points) are deliberately not applied.
func (c *Config) HeaderValue() string {
	userPass := norm.NFC.String(c.Username) + ":" + norm.NFC.String(c.Password)
	return "Basic " + base64.StdEncoding.EncodeToString([]byte(userPass))
}
