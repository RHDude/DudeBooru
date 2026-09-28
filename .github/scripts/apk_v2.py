#!/usr/bin/env python3
"""
APK Signature Scheme v2 без закрытого ключа на сборщике.

Ключ подписи хранится у владельца, а не в CI. Сборка печатает хеш содержимого APK,
владелец подписывает «подписанные данные» своим ключом и возвращает в репозиторий только
подпись (это открытые данные). Сборщик вставляет её в APK и проверяет apksigner'ом.

    apk_v2.py digest APK                        хеш содержимого (CHUNKED_SHA256), hex
    apk_v2.py signed-data DIGEST CERT.pem OUT   подписанные данные для этого хеша (что подписывать)
    apk_v2.py assemble APK CERT.pem SIG OUT     вставить подпись (DER, hex или файл) и записать APK
    apk_v2.py keystore KEY CERT.pem OUT.p12     PKCS#12 для apksigner из ключа (hex P-256 или PEM)

Формат: https://source.android.com/docs/security/features/apksigning/v2
Ключ — EC P-256, алгоритм 0x0201 (ECDSA with SHA2-256), для Android 7.0+.
"""
import hashlib
import os
import struct
import subprocess
import sys
import tempfile

CHUNK = 1024 * 1024
ECDSA_SHA256 = 0x0201
V2_BLOCK_ID = 0x7109871A
MAGIC = b"APK Sig Block 42"
EOCD_SIG = b"PK\x05\x06"


def u32(value):
    return struct.pack("<I", value)


def u64(value):
    return struct.pack("<Q", value)


def lp(data):
    """Поле с длиной uint32 little-endian впереди."""
    return u32(len(data)) + data


def zip_sections(apk):
    """Смещения: начало центрального каталога, начало EOCD. APK не должен быть уже подписан."""
    eocd = apk.rfind(EOCD_SIG, max(0, len(apk) - 65536 - 22))
    if eocd < 0:
        sys.exit("EOCD not found: not a zip")
    cd_size, cd_offset = struct.unpack("<II", apk[eocd + 12:eocd + 20])
    if cd_offset + cd_size != eocd:
        sys.exit("central directory is not right before EOCD")
    if cd_offset >= 24 and apk[cd_offset - 16:cd_offset] == MAGIC:
        sys.exit("APK already has a signing block")
    return cd_offset, eocd


def content_digest(apk):
    """CHUNKED_SHA256 по записям ZIP, центральному каталогу и EOCD, куски по 1 МиБ."""
    cd_offset, eocd = zip_sections(apk)
    # Смещение каталога в EOCD считается указывающим на начало блока подписи — в неподписанном
    # APK это то же самое место, так что EOCD берётся как есть.
    sections = [apk[:cd_offset], apk[cd_offset:eocd], apk[eocd:]]
    chunks = []
    for section in sections:
        for start in range(0, len(section), CHUNK):
            piece = section[start:start + CHUNK]
            chunks.append(hashlib.sha256(b"\xa5" + u32(len(piece)) + piece).digest())
    return hashlib.sha256(b"\x5a" + u32(len(chunks)) + b"".join(chunks)).digest()


def openssl(*args, data=None):
    return subprocess.run(["openssl", *args], input=data, capture_output=True, check=True).stdout


def cert_der(cert_pem):
    return openssl("x509", "-in", cert_pem, "-outform", "DER")


def public_key_der(cert_pem):
    pem = openssl("x509", "-in", cert_pem, "-pubkey", "-noout")
    return openssl("pkey", "-pubin", "-outform", "DER", data=pem)


def signed_data(digest, cert):
    """Подписанные данные v2: хеши, сертификаты, доп. атрибуты — каждое как последовательность с длинами."""
    digests = lp(lp(u32(ECDSA_SHA256) + lp(digest)))
    certificates = lp(lp(cert))
    attributes = lp(b"")
    return digests + certificates + attributes


def verify_signature(data, signature, cert_pem):
    with tempfile.TemporaryDirectory() as tmp:
        key = os.path.join(tmp, "pub.pem")
        with open(key, "wb") as f:
            f.write(openssl("x509", "-in", cert_pem, "-pubkey", "-noout"))
        sig = os.path.join(tmp, "sig.der")
        with open(sig, "wb") as f:
            f.write(signature)
        result = subprocess.run(["openssl", "dgst", "-sha256", "-verify", key, "-signature", sig], input=data, capture_output=True)
        return result.returncode == 0


def signing_block(data, signature, public_key):
    signer = lp(data) + lp(lp(u32(ECDSA_SHA256) + lp(signature))) + lp(public_key)
    v2_block = lp(lp(signer))
    pair = u64(4 + len(v2_block)) + u32(V2_BLOCK_ID) + v2_block
    size = len(pair) + 8 + len(MAGIC)
    return u64(size) + pair + u64(size) + MAGIC


def read_signature(value):
    if os.path.exists(value):
        with open(value, "rb") as f:
            return f.read()
    return bytes.fromhex(value.strip())


def cmd_digest(path):
    with open(path, "rb") as f:
        print(content_digest(f.read()).hex())


def cmd_signed_data(digest_hex, cert_pem, out):
    with open(out, "wb") as f:
        f.write(signed_data(bytes.fromhex(digest_hex.strip()), cert_der(cert_pem)))


def cmd_assemble(path, cert_pem, signature_value, out):
    with open(path, "rb") as f:
        apk = f.read()
    cd_offset, eocd = zip_sections(apk)
    data = signed_data(content_digest(apk), cert_der(cert_pem))
    signature = read_signature(signature_value)
    if not verify_signature(data, signature, cert_pem):
        sys.exit("signature does not match this APK and certificate")
    block = signing_block(data, signature, public_key_der(cert_pem))
    new_eocd = bytearray(apk[eocd:])
    new_eocd[16:20] = u32(cd_offset + len(block))
    with open(out, "wb") as f:
        f.write(apk[:cd_offset] + block + apk[cd_offset:eocd] + bytes(new_eocd))
    print(f"signed: {out}")


def cmd_keystore(key_value, cert_pem, out):
    """Ключ из секрета CI: 64 hex-символа (закрытое число P-256) или PEM."""
    key_value = key_value.strip()
    with tempfile.TemporaryDirectory() as tmp:
        key_pem = os.path.join(tmp, "key.pem")
        if key_value.startswith("-----BEGIN"):
            with open(key_pem, "w") as f:
                f.write(key_value + "\n")
        else:
            scalar = bytes.fromhex(key_value)
            if len(scalar) != 32:
                sys.exit("expected a 32-byte P-256 private key in hex")
            # ECPrivateKey (RFC 5915) без открытой части — OpenSSL вычислит её сам.
            sec1 = b"\x30\x31\x02\x01\x01\x04\x20" + scalar + b"\xa0\x0a\x06\x08\x2a\x86\x48\xce\x3d\x03\x01\x07"
            with open(key_pem, "wb") as f:
                f.write(openssl("ec", "-inform", "DER", "-outform", "PEM", data=sec1))
        openssl("pkcs12", "-export", "-inkey", key_pem, "-in", cert_pem, "-name", "dudebooru",
                "-passout", "pass:dudebooru", "-out", out)
    print(f"keystore: {out}")


def main(argv):
    commands = {
        "digest": (cmd_digest, 1),
        "signed-data": (cmd_signed_data, 3),
        "assemble": (cmd_assemble, 4),
        "keystore": (cmd_keystore, 3),
    }
    if len(argv) < 2 or argv[1] not in commands or len(argv) - 2 != commands[argv[1]][1]:
        sys.exit(__doc__)
    fn, _ = commands[argv[1]]
    fn(*argv[2:])


if __name__ == "__main__":
    main(sys.argv)
