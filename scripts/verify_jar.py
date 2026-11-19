#!/usr/bin/env python3
"""Uretilen JAR'in yapisini dogrular (CI)."""
import sys
import zipfile

path = sys.argv[1]
z = zipfile.ZipFile(path)
names = set(z.namelist())
errors = []

def need(n):
    if n not in names:
        errors.append("missing: " + n)

for n in ["plugin.yml", "config.yml", "lang/tr.yml", "lang/en.yml",
          "com/jestron/kayit/KayitPlugin.class",
          "com/jestron/kayit/libs/sunmail/smtp/SMTPTransport.class",
          "com/jestron/kayit/libs/sunmail/smtp/SMTPSSLTransport.class",
          "com/jestron/kayit/libs/javamail/Session.class",
          "com/jestron/kayit/libs/activation/DataHandler.class",
          "com/jestron/kayit/libs/bcrypt/BCrypt.class"]:
    need(n)

plugin_yml = z.read("plugin.yml").decode("utf-8")
if "${project.version}" in plugin_yml:
    errors.append("plugin.yml version not filtered")

for n in names:
    if n.startswith(("javax/", "com/sun/", "org/mindrot/", "org/sqlite/", "com/mysql/")):
        errors.append("unrelocated dependency: " + n)
    if n.startswith("META-INF/javamail") or n.startswith("META-INF/mailcap"):
        errors.append("stale javamail resource: " + n)
    if n.endswith(".class"):
        data = z.read(n)
        major = int.from_bytes(data[6:8], "big")
        if major > 52:
            errors.append("class newer than Java 8 (%d): %s" % (major, n))

if errors:
    for e in errors[:50]:
        print("::error::" + e)
    sys.exit(1)
print("JAR OK: %d entries, Java 8 bytecode, dependencies relocated" % len(names))
