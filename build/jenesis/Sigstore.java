package build.jenesis;

import module java.base;

import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;

public final class Sigstore {

    public record Attestation(String identity, String issuer, String log, long index, Instant recorded) { }

    private record Signer(String identity, String issuer) { }

    private final URI trustedRoot;
    private final String identity;
    private final String issuer;

    public Sigstore() {
        this(null, null, null);
    }

    private Sigstore(URI trustedRoot, String identity, String issuer) {
        this.trustedRoot = trustedRoot;
        this.identity = identity;
        this.issuer = issuer;
    }

    public Sigstore trustedRoot(URI trustedRoot) {
        return new Sigstore(trustedRoot, identity, issuer);
    }

    public Sigstore identity(String identity) {
        return new Sigstore(trustedRoot, identity, issuer);
    }

    public Sigstore issuer(String issuer) {
        return new Sigstore(trustedRoot, identity, issuer);
    }

    public Attestation verify(Path artifact, Path bundle) throws IOException, GeneralSecurityException {
        return verify(artifact, bundle, _ -> { });
    }

    public Attestation verify(Path artifact, Path bundle, Consumer<String> listener)
            throws IOException, GeneralSecurityException {
        Object envelope = Json.parse(Files.readString(bundle));
        String mediaType = text(envelope, "mediaType");
        if (!mediaType.startsWith("application/vnd.dev.sigstore.bundle")) {
            throw new IllegalArgumentException(bundle + " declares " + mediaType
                    + ", expected a media type starting with application/vnd.dev.sigstore.bundle");
        }
        List<?> entries = (List<?>) at(envelope, "verificationMaterial", "tlogEntries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(bundle + " records no transparency log entry,"
                    + " so nothing states when its certificate was still valid");
        }
        Object anchors = Json.parse(anchors());
        Object entry = entries.getFirst();
        long index = number(entry, "logIndex");
        Instant recorded = Instant.ofEpochSecond(number(entry, "integratedTime"));

        byte[] content = Files.readAllBytes(artifact);
        byte[] digest = digest(content);
        if (!Arrays.equals(digest, binary(envelope, "messageSignature", "messageDigest", "digest"))) {
            throw new IllegalStateException(artifact + " does not hash to the digest recorded in " + bundle);
        }
        listener.accept("artifact hashes to " + hex(digest));

        X509Certificate leaf = leaf(envelope);
        byte[] signature = binary(envelope, "messageSignature", "signature");
        if (!signed(leaf.getPublicKey(), content, signature)) {
            throw new IllegalStateException(artifact + " is not signed by the key in the certificate of " + bundle);
        }
        listener.accept("signature verifies under the " + leaf.getPublicKey().getAlgorithm()
                + " key of a certificate that expired " + leaf.getNotAfter().toInstant());

        List<X509Certificate> authority = authority(anchors, recorded);
        chain(leaf, authority, recorded);
        listener.accept("certificate chains to " + authority.getLast().getSubjectX500Principal()
                + " as of " + recorded + ", inside a window of " + Duration.between(
                        leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant()).toMinutes() + " minutes");

        Signer signer = signer(leaf);
        if (identity != null && !identity.equals(signer.identity())) {
            throw new IllegalStateException(artifact + " was signed by " + signer.identity()
                    + " but " + identity + " is expected; declare the identity that signs it to accept this signer");
        }
        if (issuer != null && !issuer.equals(signer.issuer())) {
            throw new IllegalStateException(artifact + " was authenticated by " + signer.issuer()
                    + " but " + issuer + " is expected");
        }
        listener.accept("identity " + signer.identity() + " authenticated by " + signer.issuer());

        byte[] logId = binary(entry, "logId", "keyId");
        Object log = log(anchors, logId);
        PublicKey key = KeyFactory.getInstance(
                        text(log, "publicKey", "keyDetails").contains("ED25519") ? "Ed25519" : "EC")
                .generatePublic(new X509EncodedKeySpec(
                        Base64.getDecoder().decode(text(log, "publicKey", "rawBytes"))));
        listener.accept("log " + text(log, "baseUrl") + " named by key " + hex(logId));

        byte[] body = binary(entry, "canonicalizedBody");
        binds(body, digest, signature, leaf);
        listener.accept("entry " + index + " records this artifact, this signature and this certificate");

        String promise = "{\"body\":\"" + text(entry, "canonicalizedBody")
                + "\",\"integratedTime\":" + recorded.getEpochSecond()
                + ",\"logID\":\"" + hex(logId)
                + "\",\"logIndex\":" + index + "}";
        if (!signed(key,
                promise.getBytes(StandardCharsets.UTF_8),
                binary(entry, "inclusionPromise", "signedEntryTimestamp"))) {
            throw new IllegalStateException("The log did not countersign entry " + index + " of " + bundle);
        }
        listener.accept("log countersigned the entry at " + recorded);

        byte[] published = binary(entry, "inclusionProof", "rootHash");
        List<byte[]> hashes = ((List<?>) at(entry, "inclusionProof", "hashes")).stream()
                .map(hash -> Base64.getDecoder().decode((String) hash))
                .toList();
        long size = number(entry, "inclusionProof", "treeSize");
        if (!Arrays.equals(published, root(digest(concat(new byte[] {0}, body)),
                number(entry, "inclusionProof", "logIndex"),
                size,
                hashes))) {
            throw new IllegalStateException("The inclusion proof of entry " + index
                    + " does not reach the root hash the log published");
        }
        listener.accept(hashes.size() + " hashes place the entry in a tree of " + size + " at root " + hex(published));

        checkpoint(text(entry, "inclusionProof", "checkpoint", "envelope"), published, size, key);
        listener.accept("log signed a checkpoint over that root");

        return new Attestation(signer.identity(), signer.issuer(), text(log, "baseUrl"), index, recorded);
    }

    private String anchors() throws IOException {
        if (trustedRoot == null) {
            return TRUSTED_ROOT;
        }
        if ("file".equals(trustedRoot.getScheme())) {
            return Files.readString(Path.of(trustedRoot));
        }
        try (InputStream stream = Repository.open(trustedRoot, null)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static X509Certificate leaf(Object envelope) throws CertificateException {
        Object material = at(envelope, "verificationMaterial");
        if (material instanceof Map<?, ?> map && map.containsKey("certificate")) {
            return certificate(binary(material, "certificate", "rawBytes"));
        }
        List<?> chain = (List<?>) at(material, "x509CertificateChain", "certificates");
        return certificate(binary(chain.getFirst(), "rawBytes"));
    }

    private static List<X509Certificate> authority(Object anchors, Instant recorded) throws CertificateException {
        for (Object authority : (List<?>) at(anchors, "certificateAuthorities")) {
            Instant start = Instant.parse(text(authority, "validFor", "start"));
            Object end = ((Map<?, ?>) at(authority, "validFor")).get("end");
            if (start.isAfter(recorded) || end instanceof String expiry && Instant.parse(expiry).isBefore(recorded)) {
                continue;
            }
            List<X509Certificate> chain = new ArrayList<>();
            for (Object certificate : (List<?>) at(authority, "certChain", "certificates")) {
                chain.add(certificate(binary(certificate, "rawBytes")));
            }
            return chain;
        }
        throw new IllegalStateException("No certificate authority of the trust root was in use at " + recorded);
    }

    private static void chain(X509Certificate leaf, List<X509Certificate> authority, Instant recorded)
            throws GeneralSecurityException {
        List<X509Certificate> path = new ArrayList<>();
        path.add(leaf);
        path.addAll(authority.subList(0, authority.size() - 1));
        PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(authority.getLast(), null)));
        parameters.setRevocationEnabled(false);
        parameters.setDate(Date.from(recorded));
        CertPathValidator.getInstance("PKIX").validate(
                CertificateFactory.getInstance("X.509").generateCertPath(path), parameters);
    }

    private static void binds(byte[] body, byte[] digest, byte[] signature, X509Certificate leaf)
            throws CertificateException {
        Object record = Json.parse(new String(body, StandardCharsets.UTF_8));
        String kind = text(record, "kind");
        if (!kind.equals("hashedrekord")) {
            throw new IllegalArgumentException("Log entries of kind " + kind
                    + " are not read, expected hashedrekord");
        }
        if (!hex(digest).equals(text(record, "spec", "data", "hash", "value"))) {
            throw new IllegalStateException("The log entry records a different artifact");
        }
        if (!Arrays.equals(signature, binary(record, "spec", "signature", "content"))) {
            throw new IllegalStateException("The log entry records a different signature");
        }
        if (!Arrays.equals(leaf.getEncoded(),
                certificate(binary(record, "spec", "signature", "publicKey", "content")).getEncoded())) {
            throw new IllegalStateException("The log entry records a different certificate");
        }
    }

    private static void checkpoint(String envelope, byte[] published, long size, PublicKey key)
            throws GeneralSecurityException {
        int separator = envelope.indexOf("\n\n");
        if (separator == -1) {
            throw new IllegalArgumentException("A checkpoint separates its note from its signatures by a blank line");
        }
        String note = envelope.substring(0, separator + 1);
        List<String> lines = note.lines().toList();
        if (lines.size() < 3) {
            throw new IllegalArgumentException("A checkpoint states an origin, a tree size and a root hash: " + note);
        }
        if (!lines.get(1).equals(Long.toString(size))) {
            throw new IllegalStateException("The checkpoint of " + lines.getFirst() + " signs a tree of "
                    + lines.get(1) + " entries where the inclusion proof reaches into one of " + size);
        }
        if (!Arrays.equals(published, Base64.getDecoder().decode(lines.get(2)))) {
            throw new IllegalStateException("The checkpoint of " + lines.getFirst() + " publishes a different root");
        }
        String line = envelope.substring(separator + 2).lines().findFirst().orElseThrow();
        if (!line.startsWith("\u2014 ")) {
            throw new IllegalArgumentException("A checkpoint signature line starts with an em dash: " + line);
        }
        byte[] signature = Base64.getDecoder().decode(line.substring(line.indexOf(' ', 2) + 1));
        if (!signed(key, note.getBytes(StandardCharsets.UTF_8), Arrays.copyOfRange(signature, 4, signature.length))) {
            throw new IllegalStateException("The checkpoint of " + lines.getFirst() + " is not signed by that log");
        }
    }

    private static byte[] root(byte[] leaf, long index, long size, List<byte[]> hashes) {
        if (index >= size) {
            throw new IllegalArgumentException("Entry " + index + " is outside a tree of " + size);
        }
        byte[] hash = leaf;
        long node = index, last = size - 1;
        for (byte[] sibling : hashes) {
            if (last == 0) {
                throw new IllegalStateException("The inclusion proof is longer than the tree is deep");
            }
            if ((node & 1) == 1 || node == last) {
                hash = digest(concat(new byte[] {1}, concat(sibling, hash)));
                while (node != 0 && (node & 1) == 0) {
                    node >>= 1;
                    last >>= 1;
                }
            } else {
                hash = digest(concat(new byte[] {1}, concat(hash, sibling)));
            }
            node >>= 1;
            last >>= 1;
        }
        if (last != 0) {
            throw new IllegalStateException("The inclusion proof is shorter than the tree is deep");
        }
        return hash;
    }

    private static Object log(Object anchors, byte[] logId) {
        for (Object log : (List<?>) at(anchors, "tlogs")) {
            if (Arrays.equals(logId, binary(log, "logId", "keyId"))) {
                return log;
            }
        }
        throw new IllegalStateException("No transparency log of the trust root has key " + hex(logId)
                + "; a log added since this trust root was published is accepted by a newer one,"
                + " named with -Djenesis.sigstore.uri");
    }

    private static boolean signed(PublicKey key, byte[] content, byte[] signature) throws GeneralSecurityException {
        Signature verifier = Signature.getInstance(switch (key.getAlgorithm()) {
            case "EC" -> "SHA256withECDSA";
            case "RSA" -> "SHA256withRSA";
            case "Ed25519", "EdDSA" -> "Ed25519";
            default -> throw new IllegalArgumentException("Signatures of " + key.getAlgorithm()
                    + " keys are not read, expected EC, RSA or Ed25519");
        });
        verifier.initVerify(key);
        verifier.update(content);
        return verifier.verify(signature);
    }

    private static Signer signer(X509Certificate leaf) throws CertificateParsingException {
        List<String> identities = new ArrayList<>();
        Collection<List<?>> names = leaf.getSubjectAlternativeNames();
        for (List<?> name : names == null ? List.<List<?>>of() : names) {
            if ((Integer) name.getFirst() == 6) {
                identities.add((String) name.getLast());
            }
        }
        if (identities.isEmpty()) {
            throw new IllegalStateException("The certificate names no identity URI");
        }
        if (identities.size() > 1) {
            throw new IllegalStateException("The certificate names more than one identity, so none of them is"
                    + " the signer: " + String.join(", ", identities));
        }
        String identity = identities.getFirst();
        byte[] extension = leaf.getExtensionValue("1.3.6.1.4.1.57264.1.8");
        if (extension != null) {
            return new Signer(identity, new String(unwrap(unwrap(extension)), StandardCharsets.UTF_8));
        }
        byte[] legacy = leaf.getExtensionValue("1.3.6.1.4.1.57264.1.1");
        if (legacy == null) {
            throw new IllegalStateException("The certificate of " + identity + " names no issuer that authenticated it");
        }
        return new Signer(identity, new String(unwrap(legacy), StandardCharsets.UTF_8));
    }

    private static byte[] unwrap(byte[] encoded) {
        int length = encoded[1] & 0xFF, offset = 2;
        if ((length & 0x80) != 0) {
            int size = length - 0x80;
            length = 0;
            for (int index = 0; index < size; index++) {
                length = length << 8 | encoded[offset++] & 0xFF;
            }
        }
        return Arrays.copyOfRange(encoded, offset, offset + length);
    }

    private static X509Certificate certificate(byte[] encoded) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] digest(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static String hex(byte[] content) {
        return HexFormat.of().formatHex(content);
    }

    private static Object at(Object owner, String... environment) {
        Object value = owner;
        for (String key : environment) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(key)) {
                throw new IllegalArgumentException("Expected a Sigstore document that states "
                        + String.join(".", environment));
            }
            value = map.get(key);
        }
        return value;
    }

    private static String text(Object owner, String... environment) {
        return (String) at(owner, environment);
    }

    private static byte[] binary(Object owner, String... environment) {
        return Base64.getDecoder().decode(text(owner, environment));
    }

    private static final String TRUSTED_ROOT = """
            {
              "mediaType": "application/vnd.dev.sigstore.trustedroot+json;version=0.1",
              "tlogs": [
                {
                  "baseUrl": "https://rekor.sigstore.dev",
                  "hashAlgorithm": "SHA2_256",
                  "publicKey": {
                    "rawBytes": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2G2Y+2tabdTV5BcGiBIx0a9fAFwrkBbmLSGtks4L3qX6yYY0zufBnhC8Ur/iy55GhWP/9A/bY2LhC30M9+RYtw==",
                    "keyDetails": "PKIX_ECDSA_P256_SHA_256",
                    "validFor": {
                      "start": "2021-01-12T11:53:27Z"
                    }
                  },
                  "logId": {
                    "keyId": "wNI9atQGlz+VWfO6LRygH4QUfY/8W4RFwiT5i5WRgB0="
                  }
                },
                {
                  "baseUrl": "https://log2025-1.rekor.sigstore.dev",
                  "hashAlgorithm": "SHA2_256",
                  "publicKey": {
                    "rawBytes": "MCowBQYDK2VwAyEAt8rlp1knGwjfbcXAYPYAkn0XiLz1x8O4t0YkEhie244=",
                    "keyDetails": "PKIX_ED25519",
                    "validFor": {
                      "start": "2025-09-23T00:00:00Z"
                    }
                  },
                  "logId": {
                    "keyId": "zxGZFVvd0FEmjR8WrFwMdcAJ9vtaY/QXf44Y1wUeP6A="
                  }
                }
              ],
              "certificateAuthorities": [
                {
                  "subject": {
                    "organization": "sigstore.dev",
                    "commonName": "sigstore"
                  },
                  "uri": "https://fulcio.sigstore.dev",
                  "certChain": {
                    "certificates": [
                      {
                        "rawBytes": "MIIB+DCCAX6gAwIBAgITNVkDZoCiofPDsy7dfm6geLbuhzAKBggqhkjOPQQDAzAqMRUwEwYDVQQKEwxzaWdzdG9yZS5kZXYxETAPBgNVBAMTCHNpZ3N0b3JlMB4XDTIxMDMwNzAzMjAyOVoXDTMxMDIyMzAzMjAyOVowKjEVMBMGA1UEChMMc2lnc3RvcmUuZGV2MREwDwYDVQQDEwhzaWdzdG9yZTB2MBAGByqGSM49AgEGBSuBBAAiA2IABLSyA7Ii5k+pNO8ZEWY0ylemWDowOkNa3kL+GZE5Z5GWehL9/A9bRNA3RbrsZ5i0JcastaRL7Sp5fp/jD5dxqc/UdTVnlvS16an+2Yfswe/QuLolRUCrcOE2+2iA5+tzd6NmMGQwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQEwHQYDVR0OBBYEFMjFHQBBmiQpMlEk6w2uSu1KBtPsMB8GA1UdIwQYMBaAFMjFHQBBmiQpMlEk6w2uSu1KBtPsMAoGCCqGSM49BAMDA2gAMGUCMH8liWJfMui6vXXBhjDgY4MwslmN/TJxVe/83WrFomwmNf056y1X48F9c4m3a3ozXAIxAKjRay5/aj/jsKKGIkmQatjI8uupHr/+CxFvaJWmpYqNkLDGRU+9orzh5hI2RrcuaQ=="
                      }
                    ]
                  },
                  "validFor": {
                    "start": "2021-03-07T03:20:29Z",
                    "end": "2022-12-31T23:59:59.999Z"
                  }
                },
                {
                  "subject": {
                    "organization": "sigstore.dev",
                    "commonName": "sigstore"
                  },
                  "uri": "https://fulcio.sigstore.dev",
                  "certChain": {
                    "certificates": [
                      {
                        "rawBytes": "MIICGjCCAaGgAwIBAgIUALnViVfnU0brJasmRkHrn/UnfaQwCgYIKoZIzj0EAwMwKjEVMBMGA1UEChMMc2lnc3RvcmUuZGV2MREwDwYDVQQDEwhzaWdzdG9yZTAeFw0yMjA0MTMyMDA2MTVaFw0zMTEwMDUxMzU2NThaMDcxFTATBgNVBAoTDHNpZ3N0b3JlLmRldjEeMBwGA1UEAxMVc2lnc3RvcmUtaW50ZXJtZWRpYXRlMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8RVS/ysH+NOvuDZyPIZtilgUF9NlarYpAd9HP1vBBH1U5CV77LSS7s0ZiH4nE7Hv7ptS6LvvR/STk798LVgMzLlJ4HeIfF3tHSaexLcYpSASr1kS0N/RgBJz/9jWCiXno3sweTAOBgNVHQ8BAf8EBAMCAQYwEwYDVR0lBAwwCgYIKwYBBQUHAwMwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQU39Ppz1YkEZb5qNjpKFWixi4YZD8wHwYDVR0jBBgwFoAUWMAeX5FFpWapesyQoZMi0CrFxfowCgYIKoZIzj0EAwMDZwAwZAIwPCsQK4DYiZYDPIaDi5HFKnfxXx6ASSVmERfsynYBiX2X6SJRnZU84/9DZdnFvvxmAjBOt6QpBlc4J/0DxvkTCqpclvziL6BCCPnjdlIB3Pu3BxsPmygUY7Ii2zbdCdliiow="
                      },
                      {
                        "rawBytes": "MIIB9zCCAXygAwIBAgIUALZNAPFdxHPwjeDloDwyYChAO/4wCgYIKoZIzj0EAwMwKjEVMBMGA1UEChMMc2lnc3RvcmUuZGV2MREwDwYDVQQDEwhzaWdzdG9yZTAeFw0yMTEwMDcxMzU2NTlaFw0zMTEwMDUxMzU2NThaMCoxFTATBgNVBAoTDHNpZ3N0b3JlLmRldjERMA8GA1UEAxMIc2lnc3RvcmUwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAT7XeFT4rb3PQGwS4IajtLk3/OlnpgangaBclYpsYBr5i+4ynB07ceb3LP0OIOZdxexX69c5iVuyJRQ+Hz05yi+UF3uBWAlHpiS5sh0+H2GHE7SXrk1EC5m1Tr19L9gg92jYzBhMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMBAf8EBTADAQH/MB0GA1UdDgQWBBRYwB5fkUWlZql6zJChkyLQKsXF+jAfBgNVHSMEGDAWgBRYwB5fkUWlZql6zJChkyLQKsXF+jAKBggqhkjOPQQDAwNpADBmAjEAj1nHeXZp+13NWBNa+EDsDP8G1WWg1tCMWP/WHPqpaVo0jhsweNFZgSs0eE7wYI4qAjEA2WB9ot98sIkoF3vZYdd3/VtWB5b9TNMea7Ix/stJ5TfcLLeABLE4BNJOsQ4vnBHJ"
                      }
                    ]
                  },
                  "validFor": {
                    "start": "2022-04-13T20:06:15Z"
                  }
                }
              ],
              "ctlogs": [
                {
                  "baseUrl": "https://ctfe.sigstore.dev/test",
                  "hashAlgorithm": "SHA2_256",
                  "publicKey": {
                    "rawBytes": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbfwR+RJudXscgRBRpKX1XFDy3PyudDxz/SfnRi1fT8ekpfBd2O1uoz7jr3Z8nKzxA69EUQ+eFCFI3zeubPWU7w==",
                    "keyDetails": "PKIX_ECDSA_P256_SHA_256",
                    "validFor": {
                      "start": "2021-03-14T00:00:00Z",
                      "end": "2022-10-31T23:59:59.999Z"
                    }
                  },
                  "logId": {
                    "keyId": "CGCS8ChS/2hF0dFrJ4ScRWcYrBY9wzjSbea8IgY2b3I="
                  }
                },
                {
                  "baseUrl": "https://ctfe.sigstore.dev/2022",
                  "hashAlgorithm": "SHA2_256",
                  "publicKey": {
                    "rawBytes": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEiPSlFi0CmFTfEjCUqF9HuCEcYXNKAaYalIJmBZ8yyezPjTqhxrKBpMnaocVtLJBI1eM3uXnQzQGAJdJ4gs9Fyw==",
                    "keyDetails": "PKIX_ECDSA_P256_SHA_256",
                    "validFor": {
                      "start": "2022-10-20T00:00:00Z"
                    }
                  },
                  "logId": {
                    "keyId": "3T0wasbHETJjGR4cmWc3AqJKXrjePK3/h4pygC8p7o4="
                  }
                }
              ],
              "timestampAuthorities": [
                {
                  "subject": {
                    "organization": "sigstore.dev",
                    "commonName": "sigstore-tsa-selfsigned"
                  },
                  "uri": "https://timestamp.sigstore.dev/api/v1/timestamp",
                  "certChain": {
                    "certificates": [
                      {
                        "rawBytes": "MIICEDCCAZagAwIBAgIUOhNULwyQYe68wUMvy4qOiyojiwwwCgYIKoZIzj0EAwMwOTEVMBMGA1UEChMMc2lnc3RvcmUuZGV2MSAwHgYDVQQDExdzaWdzdG9yZS10c2Etc2VsZnNpZ25lZDAeFw0yNTA0MDgwNjU5NDNaFw0zNTA0MDYwNjU5NDNaMC4xFTATBgNVBAoTDHNpZ3N0b3JlLmRldjEVMBMGA1UEAxMMc2lnc3RvcmUtdHNhMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE4ra2Z8hKNig2T9kFjCAToGG30jky+WQv3BzL+mKvh1SKNR/UwuwsfNCg4sryoYAd8E6isovVA3M4aoNdm9QDi50Z8nTEyvqgfDPtTIwXItfiW/AFf1V7uwkbkAoj0xxco2owaDAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0OBBYEFIn9eUOHz9BlRsMCRscsc1t9tOsDMB8GA1UdIwQYMBaAFJjsAe9/u1H/1JUeb4qImFMHic6/MBYGA1UdJQEB/wQMMAoGCCsGAQUFBwMIMAoGCCqGSM49BAMDA2gAMGUCMDtpsV/6KaO0qyF/UMsX2aSUXKQFdoGTptQGc0ftq1csulHPGG6dsmyMNd3JB+G3EQIxAOajvBcjpJmKb4Nv+2Taoj8Uc5+b6ih6FXCCKraSqupe07zqswMcXJTe1cExvHvvlw=="
                      },
                      {
                        "rawBytes": "MIIB9zCCAXygAwIBAgIUV7f0GLDOoEzIh8LXSW80OJiUp14wCgYIKoZIzj0EAwMwOTEVMBMGA1UEChMMc2lnc3RvcmUuZGV2MSAwHgYDVQQDExdzaWdzdG9yZS10c2Etc2VsZnNpZ25lZDAeFw0yNTA0MDgwNjU5NDNaFw0zNTA0MDYwNjU5NDNaMDkxFTATBgNVBAoTDHNpZ3N0b3JlLmRldjEgMB4GA1UEAxMXc2lnc3RvcmUtdHNhLXNlbGZzaWduZWQwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAQUQNtfRT/ou3YATa6wB/kKTe70cfJwyRIBovMnt8RcJph/COE82uyS6FmppLLL1VBPGcPfpQPYJNXzWwi8icwhKQ6W/Qe2h3oebBb2FHpwNJDqo+TMaC/tdfkv/ElJB72jRTBDMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSY7AHvf7tR/9SVHm+KiJhTB4nOvzAKBggqhkjOPQQDAwNpADBmAjEAwGEGrfGZR1cen1R8/DTVMI943LssZmJRtDp/i7SfGHmGRP6gRbuj9vOK3b67Z0QQAjEAuT2H673LQEaHTcyQSZrkp4mX7WwkmF+sVbkYY5mXN+RMH13KUEHHOqASaemYWK/E"
                      }
                    ]
                  },
                  "validFor": {
                    "start": "2025-07-04T00:00:00Z"
                  }
                }
              ]
            }
            """;

    private static long number(Object owner, String... environment) {
        Object value = at(owner, environment);
        return value instanceof Number digits ? digits.longValue() : Long.parseLong((String) value);
    }
}
