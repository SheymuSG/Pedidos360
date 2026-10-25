"""Sirve el frontend Angular desde un bucket S3 PRIVADO a traves del API Gateway.

El bucket no es publico: solo esta funcion (rol del laboratorio) puede leerlo. HTTPS lo aporta el API Gateway,
lo que permite usar la URL como redirect URI de MSAL/Entra ID (que exige HTTPS fuera de localhost).

Rutas: cualquier GET que no sea /api/*. Si el archivo no existe y la ruta no parece un archivo estatico,
se devuelve index.html (el enrutador de Angular resuelve /pedidos, /mensajeria, ...).
"""
import base64
import mimetypes
import os

import boto3

s3 = boto3.client("s3")
BUCKET = os.environ["BUCKET"]
PREFIX = os.environ.get("PREFIX", "web/")

SECURITY_HEADERS = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
}


def _get(key):
    try:
        return s3.get_object(Bucket=BUCKET, Key=PREFIX + key)
    except s3.exceptions.NoSuchKey:
        return None


def _response(status, body=b"", content_type="text/plain; charset=utf-8", cache="no-store"):
    return {
        "statusCode": status,
        "isBase64Encoded": True,
        "headers": {"Content-Type": content_type, "Cache-Control": cache, **SECURITY_HEADERS},
        "body": base64.b64encode(body).decode("ascii"),
    }


def handler(event, context):
    path = event.get("rawPath", "/").lstrip("/")
    if path.startswith("api/"):
        return _response(404, b"not found")

    key = path or "index.html"
    obj = _get(key)
    if obj is None:
        last_segment = key.rsplit("/", 1)[-1]
        if "." in last_segment:                      # archivo estatico inexistente
            return _response(404, b"not found")
        key, obj = "index.html", _get("index.html")  # ruta de Angular -> index.html
        if obj is None:
            return _response(404, b"frontend no desplegado")

    ctype = mimetypes.guess_type(key)[0] or "application/octet-stream"
    if ctype.startswith("text/") or ctype in ("application/javascript", "application/json", "image/svg+xml"):
        ctype += "; charset=utf-8"
    cache = "no-store" if key == "index.html" else "public, max-age=31536000, immutable"
    return _response(200, obj["Body"].read(), ctype, cache)
