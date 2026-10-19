"""Verify the APK v2 signature/content, identity and ZIP integrity without Android SDK."""
import hashlib, struct, sys, zipfile, json
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, ec

def u32(data, pos=0): return struct.unpack_from('<I', data, pos)[0]
def u64(data, pos=0): return struct.unpack_from('<Q', data, pos)[0]
def lp(data, pos=0):
    size = u32(data, pos)
    end = pos + 4 + size
    assert end <= len(data)
    return data[pos + 4:end], end
def sequence(data):
    pos = 0
    while pos < len(data):
        item, pos = lp(data, pos)
        yield item
    assert pos == len(data)

def manifest(data):
    pos = 8; strings = []; result = {}
    while pos < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, pos)
        assert size >= header and size > 0
        if kind == 1:
            count, styles, flags, start = struct.unpack_from('<4I', data, pos + 8)
            strings = []
            for i in range(count):
                p = pos + start + u32(data, pos + header + 4 * i)
                if flags & 0x100:
                    for j in range(2):
                        n = data[p]; p += 1
                        if n & 128: n = ((n & 127) << 8) | data[p]; p += 1
                    value = data[p:p + n].decode('utf-8')
                else:
                    n = struct.unpack_from('<H', data, p)[0]; p += 2
                    if n & 0x8000:
                        n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', data, p)[0]; p += 2
                    value = data[p:p + n * 2].decode('utf-16-le')
                strings.append(value)
        elif kind == 0x102:
            name = strings[u32(data, pos + 20)]
            attr_start, attr_size, count = struct.unpack_from('<3H', data, pos + 24)
            attrs = {}
            for i in range(count):
                p = pos + 16 + attr_start + i * attr_size
                key = strings[u32(data, p + 4)]; raw = u32(data, p + 8)
                value = u32(data, p + 16)
                attrs[key] = strings[raw] if raw != 0xffffffff else strings[value] if data[p+15] == 3 else value
            if name == 'manifest': result.update({k:attrs[k] for k in ('package','versionCode','versionName')})
            if name == 'uses-sdk': result.update(attrs)
            if name == 'application': result['application_label'] = attrs.get('label')
            if name == 'uses-permission': result.setdefault('permissions', []).append(attrs.get('name'))
            if name == 'activity' and attrs.get('name', '').startswith('com.futoonpay.admin.'):
                result.setdefault('native_activities', []).append(attrs)
        pos += size
    return result

path = Path(sys.argv[1]); data = path.read_bytes()
end = data.rfind(b'PK\x05\x06'); assert end >= 0
assert end + 22 + struct.unpack_from('<H', data, end + 20)[0] == len(data)
cd = u32(data, end + 16)
assert data[cd - 16:cd] == b'APK Sig Block 42'
block_size = u64(data, cd - 24); block = cd - block_size - 8
assert u64(data, block) == block_size
pos = block + 8; v2 = None
while pos < cd - 24:
    size = u64(data, pos); key = u32(data, pos + 8)
    if key == 0x7109871a: v2 = data[pos + 12:pos + 8 + size]
    pos += 8 + size
assert v2 is not None
signers, end_signers = lp(v2); assert end_signers == len(v2)
fingerprints = []
for signer in sequence(signers):
    signed, p = lp(signer); signatures, p = lp(signer, p); public, p = lp(signer, p)
    assert p == len(signer)
    digests, q = lp(signed); certs, q = lp(signed, q); attrs, q = lp(signed, q)
    assert signed[q:] == b'\0' * (len(signed) - q), 'Unexpected signed-data trailer'
    cert = x509.load_der_x509_certificate(next(sequence(certs)))
    public_key = cert.public_key()
    assert public_key.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo) == public
    checked = False
    for signature in sequence(signatures):
        alg = u32(signature); signature, _ = lp(signature, 4)
        if alg not in (0x101,0x102,0x103,0x104,0x201,0x202): continue
        sha512 = alg in (0x102,0x104,0x202)
        hash_alg = hashes.SHA512() if sha512 else hashes.SHA256()
        hash_func = hashlib.sha512 if sha512 else hashlib.sha256
        if alg in (0x201,0x202): public_key.verify(signature, signed, ec.ECDSA(hash_alg))
        else:
            pad = padding.PSS(mgf=padding.MGF1(hash_alg), salt_length=hash_alg.digest_size) if alg in (0x101,0x102) else padding.PKCS1v15()
            public_key.verify(signature, signed, pad, hash_alg)
        expected = None
        for digest in sequence(digests):
            if u32(digest) == alg: expected, _ = lp(digest, 4)
        adjusted_end = bytearray(data[end:]); struct.pack_into('<I', adjusted_end, 16, block)
        chunks = []
        for section in (data[:block], data[cd:end], bytes(adjusted_end)):
            for start in range(0,len(section),1024*1024):
                chunk = section[start:start+1024*1024]
                chunks.append(hash_func(b'\xa5' + struct.pack('<I',len(chunk)) + chunk).digest())
        actual = hash_func(b'\x5a' + struct.pack('<I',len(chunks)) + b''.join(chunks)).digest()
        assert expected == actual, 'APK signed content digest mismatch'
        checked = True
    assert checked, 'No supported verified APK v2 signature'
    fingerprints.append(cert.fingerprint(hashes.SHA256()).hex())
expected_signer = 'a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab'
assert fingerprints == [expected_signer], 'Signing certificate changed'
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None
    identity = manifest(z.read('AndroidManifest.xml'))
    assert identity['package'] == 'com.futoonpay.vinechat.beta'
    assert identity['versionCode'] == 71909, identity
    assert identity['application_label'] == '短信', identity
    assert identity['minSdkVersion'] == 23 and identity['targetSdkVersion'] == 36
    assert not identity.get('native_activities'), 'Standalone business activities must not return'
    for permission in ['android.permission.RECORD_AUDIO', 'android.permission.READ_CONTACTS', 'android.permission.WRITE_CONTACTS']:
        assert permission not in identity.get('permissions', []), permission
    abis = sorted({n.split('/')[1] for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')})
    assert abis == ['arm64-v8a'], abis
    assert not any(n.startswith('assets/futoon/') for n in z.namelist()), 'Obsolete HTML business assets returned'
    dex = b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
    operations = ['chat.tools.read','ui.draft','local.read','local.write','delivery.read','orders.search','order.detail','merchant.lookup','merchant.balance','merchant.profile','form.save','evidence.draft','evidence.restore','ui.source','ocr.gallery','channel.test','module.editor']
    for operation in operations: assert operation.encode() + b'\0' in dex, operation
    for descriptor in ['Lorg/telegram/ui/FutoonThemeBridge;', 'Lorg/telegram/ui/FutoonQuickReplies;', 'Lorg/telegram/ui/FutoonChatSettingsActivity;']:
        assert descriptor.encode() + b'\0' in dex, descriptor
    fixed_pattern = b'\\{([^{}\\n]+)\\}'
    old_pattern = b'\\{([^{}\\n]+)}'
    assert fixed_pattern + b'\0' in dex, 'Fixed Android ICU template expression missing'
    assert old_pattern + b'\0' not in dex, 'Original invalid ICU expression returned'
    native_core_bytes = len(z.read('lib/arm64-v8a/libtmessages.49.so'))
    identity.pop('permissions', None)
print(json.dumps(dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), signer_sha256=fingerprints[0], v2_signature='verified', zip_crc='verified', abis=abis, futoon_assets=0, native_core_bytes=native_core_bytes, native_operation_markers=len(operations), **identity), ensure_ascii=False, indent=2))
