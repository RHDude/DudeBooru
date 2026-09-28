#!/usr/bin/env bash
# Выпуск в GitHub Releases: тег v<версия> на коммите сборки, подписанный APK и заметки
# из .github/release/notes/<версия>.md; внизу — SHA-256 файла и отпечаток сертификата подписи.
set -euo pipefail

version="$1"
apk="$2"
commit="$3"

notes=".github/release/notes/$version.md"
apk_sha=$(sha256sum "$apk" | cut -d' ' -f1)
cert_sha=$(openssl x509 -in .github/release/signing-cert.pem -outform DER | sha256sum | cut -d' ' -f1)

{
  if [ -f "$notes" ]; then cat "$notes"; else echo "DudeBooru $version"; fi
  echo
  echo "---"
  echo "APK SHA-256: \`$apk_sha\`"
  echo
  echo "Signing certificate SHA-256: \`$cert_sha\`"
} > release-notes.md

if gh release view "v$version" > /dev/null 2>&1; then
  echo "::warning::Release v$version already exists — replacing the APK"
  gh release upload "v$version" "$apk" --clobber
else
  gh release create "v$version" "$apk" --target "$commit" --title "DudeBooru $version" --notes-file release-notes.md
fi
