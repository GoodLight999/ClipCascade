#!/usr/bin/env python3
"""Generate the public, deterministic Extended test signing identity.

The key is intentionally reproducible and public. It is not a production trust
anchor. Its only purpose is to let CI test APKs update one another without
Android reporting a signature/package conflict on every build.
"""

from __future__ import annotations

import base64
import hashlib
import math
import os
import sys
from pathlib import Path

SEED = b"ClipCascade Extended public CI test signing identity v1"
PUBLIC_EXPONENT = 65537
BITS = 2048
EXPECTED_CERT_SHA256 = (
    "b2fd5bc5d218c18e515d46a3c431bcadc1e68d847e2dd81374785d463b2bb9b0"
)


def _length(value: int) -> bytes:
    if value < 0x80:
        return bytes([value])
    encoded = value.to_bytes((value.bit_length() + 7) // 8, "big")
    return bytes([0x80 | len(encoded)]) + encoded


def _tlv(tag: int, value: bytes) -> bytes:
    return bytes([tag]) + _length(len(value)) + value


def _seq(*values: bytes) -> bytes:
    return _tlv(0x30, b"".join(values))


def _set(*values: bytes) -> bytes:
    return _tlv(0x31, b"".join(values))


def _integer(value: int) -> bytes:
    if value == 0:
        raw = b"\x00"
    else:
        raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
        if raw[0] & 0x80:
            raw = b"\x00" + raw
    return _tlv(0x02, raw)


def _null() -> bytes:
    return b"\x05\x00"


def _octet_string(value: bytes) -> bytes:
    return _tlv(0x04, value)


def _bit_string(value: bytes) -> bytes:
    return _tlv(0x03, b"\x00" + value)


def _utf8(value: str) -> bytes:
    return _tlv(0x0C, value.encode("utf-8"))


def _generalized_time(value: str) -> bytes:
    return _tlv(0x18, value.encode("ascii"))


def _oid(*arcs: int) -> bytes:
    if len(arcs) < 2:
        raise ValueError("OID requires at least two arcs")
    body = bytes([40 * arcs[0] + arcs[1]])
    for arc in arcs[2:]:
        encoded = [arc & 0x7F]
        arc >>= 7
        while arc:
            encoded.append(0x80 | (arc & 0x7F))
            arc >>= 7
        body += bytes(reversed(encoded))
    return _tlv(0x06, body)


RSA_ENCRYPTION = _seq(_oid(1, 2, 840, 113549, 1, 1, 1), _null())
SHA256_WITH_RSA = _seq(_oid(1, 2, 840, 113549, 1, 1, 11), _null())
COMMON_NAME = _oid(2, 5, 4, 3)
ORGANIZATION = _oid(2, 5, 4, 10)


def _candidate(label: bytes, counter: int, bits: int) -> int:
    byte_count = bits // 8
    stream = hashlib.shake_256(
        SEED + b"/" + label + counter.to_bytes(8, "big")
    ).digest(byte_count)
    value = int.from_bytes(stream, "big")
    value |= 1
    value |= 1 << (bits - 1)
    value |= 1 << (bits - 2)
    return value


def _is_probable_prime(value: int) -> bool:
    small_primes = (3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47)
    for prime in small_primes:
        if value == prime:
            return True
        if value % prime == 0:
            return False

    d = value - 1
    s = 0
    while d % 2 == 0:
        s += 1
        d //= 2

    bases = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53]
    encoded_value = value.to_bytes((value.bit_length() + 7) // 8, "big")
    for index in range(24):
        digest = hashlib.sha256(
            SEED + b"/mr/" + index.to_bytes(4, "big") + encoded_value
        ).digest()
        bases.append(2 + int.from_bytes(digest, "big") % (value - 3))

    for base in bases:
        if base >= value - 1:
            continue
        x = pow(base, d, value)
        if x in (1, value - 1):
            continue
        for _ in range(s - 1):
            x = pow(x, 2, value)
            if x == value - 1:
                break
        else:
            return False
    return True


def _prime(label: bytes, bits: int) -> int:
    counter = 0
    while True:
        value = _candidate(label, counter, bits)
        if math.gcd(value - 1, PUBLIC_EXPONENT) == 1 and _is_probable_prime(value):
            return value
        counter += 1


def _rsa_numbers() -> tuple[int, int, int, int, int, int, int, int]:
    p = _prime(b"p", BITS // 2)
    q = _prime(b"q", BITS // 2)
    if p == q:
        raise RuntimeError("Deterministic RSA primes unexpectedly matched")
    if p < q:
        p, q = q, p
    modulus = p * q
    phi = (p - 1) * (q - 1)
    private_exponent = pow(PUBLIC_EXPONENT, -1, phi)
    return (
        modulus,
        PUBLIC_EXPONENT,
        private_exponent,
        p,
        q,
        private_exponent % (p - 1),
        private_exponent % (q - 1),
        pow(q, -1, p),
    )


def _name() -> bytes:
    return _seq(
        _set(_seq(COMMON_NAME, _utf8("ClipCascade Extended Public Test Signing"))),
        _set(_seq(ORGANIZATION, _utf8("GoodLight999 / public CI test key"))),
    )


def _pkcs8_and_certificate() -> tuple[bytes, bytes]:
    modulus, exponent, private_exponent, p, q, dp, dq, qi = _rsa_numbers()
    rsa_private = _seq(
        _integer(0),
        _integer(modulus),
        _integer(exponent),
        _integer(private_exponent),
        _integer(p),
        _integer(q),
        _integer(dp),
        _integer(dq),
        _integer(qi),
    )
    pkcs8 = _seq(_integer(0), RSA_ENCRYPTION, _octet_string(rsa_private))

    public_key = _seq(_integer(modulus), _integer(exponent))
    subject_public_key_info = _seq(RSA_ENCRYPTION, _bit_string(public_key))
    name = _name()
    serial = int.from_bytes(
        hashlib.sha256(SEED + b"/serial").digest()[:16], "big"
    ) >> 1
    tbs_certificate = _seq(
        _tlv(0xA0, _integer(2)),
        _integer(serial),
        SHA256_WITH_RSA,
        name,
        _seq(
            _generalized_time("20260101000000Z"),
            _generalized_time("20530101000000Z"),
        ),
        name,
        subject_public_key_info,
    )

    digest_info = (
        bytes.fromhex("3031300d060960864801650304020105000420")
        + hashlib.sha256(tbs_certificate).digest()
    )
    modulus_bytes = (modulus.bit_length() + 7) // 8
    padding = b"\xff" * (modulus_bytes - len(digest_info) - 3)
    encoded_message = b"\x00\x01" + padding + b"\x00" + digest_info
    signature = pow(
        int.from_bytes(encoded_message, "big"), private_exponent, modulus
    ).to_bytes(modulus_bytes, "big")
    certificate = _seq(
        tbs_certificate,
        SHA256_WITH_RSA,
        _bit_string(signature),
    )
    return pkcs8, certificate


def _pem(label: str, der: bytes) -> bytes:
    encoded = base64.b64encode(der).decode("ascii")
    lines = [encoded[index:index + 64] for index in range(0, len(encoded), 64)]
    return (
        f"-----BEGIN {label}-----\n"
        + "\n".join(lines)
        + f"\n-----END {label}-----\n"
    ).encode("ascii")


def main() -> int:
    if len(sys.argv) != 2:
        print(
            "usage: generate_stable_test_signing.py OUTPUT_DIRECTORY",
            file=sys.stderr,
        )
        return 2

    output = Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)
    private_key, certificate = _pkcs8_and_certificate()
    digest = hashlib.sha256(certificate).hexdigest()
    if digest != EXPECTED_CERT_SHA256:
        raise RuntimeError(f"Unexpected deterministic certificate digest: {digest}")

    private_der_path = output / "clipcascade-extended-test.pk8"
    private_pem_path = output / "clipcascade-extended-test.key.pem"
    certificate_path = output / "clipcascade-extended-test.x509.pem"
    private_der_path.write_bytes(private_key)
    private_pem_path.write_bytes(_pem("PRIVATE KEY", private_key))
    certificate_path.write_bytes(_pem("CERTIFICATE", certificate))
    for path in (private_der_path, private_pem_path):
        try:
            os.chmod(path, 0o600)
        except OSError:
            pass

    print(f"Generated public CI test signing identity: sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
