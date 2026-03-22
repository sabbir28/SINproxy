import time
import os
from typing import Optional, Tuple, Any
from OpenSSL import crypto
from sinproxy import config
from sinproxy.utils.logger import log

class CertificateHelper:
    """Professional CA-based certificate management for MITM"""

    @staticmethod
    def _ensure_ca_dir():
        """Creates the certs directory if it doesn't exist"""
        cert_dir = os.path.dirname(config.ROOT_CA_CRT)
        if not os.path.exists(cert_dir):
            os.makedirs(cert_dir, exist_ok=True)

    @staticmethod
    def _ensure_ca() -> Tuple[Any, Any]:
        """Ensures the Root CA exists, generating it if necessary"""
        CertificateHelper._ensure_ca_dir()
        ca_cert_path = config.ROOT_CA_CRT
        ca_key_path = config.ROOT_CA_KEY

        if os.path.exists(ca_cert_path) and os.path.exists(ca_key_path):
            with open(ca_cert_path, "rb") as f:
                ca_cert = crypto.load_certificate(crypto.FILETYPE_PEM, f.read())
            with open(ca_key_path, "rb") as f:
                ca_key = crypto.load_privatekey(crypto.FILETYPE_PEM, f.read())
            return ca_cert, ca_key

        log("Generating new Root CA for SINproxy...")
        ca_key = crypto.PKey()
        ca_key.generate_key(crypto.TYPE_RSA, 2048)

        ca_cert = crypto.X509()
        ca_cert.set_version(2)
        ca_cert.get_subject().CN = "SINproxy Root CA"
        ca_cert.set_serial_number(100)
        ca_cert.gmtime_adj_notBefore(0)
        ca_cert.gmtime_adj_notAfter(10 * 365 * 24 * 60 * 60) # 10 years
        ca_cert.set_issuer(ca_cert.get_subject())
        ca_cert.set_pubkey(ca_key)
        
        ca_cert.add_extensions([
            crypto.X509Extension(b"basicConstraints", True, b"CA:TRUE, pathlen:0"),
            crypto.X509Extension(b"keyUsage", True, b"keyCertSign, cRLSign"),
            crypto.X509Extension(b"subjectKeyIdentifier", False, b"hash", subject=ca_cert),
        ])
        
        ca_cert.sign(ca_key, 'sha256')

        with open(ca_cert_path, "wb") as f:
            f.write(crypto.dump_certificate(crypto.FILETYPE_PEM, ca_cert))
        with open(ca_key_path, "wb") as f:
            f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, ca_key))
        
        log(f"Root CA created: {ca_cert_path}. PLEASE TRUST THIS IN YOUR BROWSER!")
        return ca_cert, ca_key

    @staticmethod
    def generate_certificate_data(common_name: str) -> Tuple[Any, Any]:
        """Generates a host certificate in memory for thread-safe MITM"""
        try:
            ca_cert, ca_key = CertificateHelper._ensure_ca()
            
            key = crypto.PKey()
            key.generate_key(crypto.TYPE_RSA, 2048)

            cert = crypto.X509()
            cert.set_version(2)
            cert.get_subject().CN = common_name
            cert.set_serial_number(int(time.time() * 100))
            cert.gmtime_adj_notBefore(0)
            cert.gmtime_adj_notAfter(365 * 24 * 60 * 60) # 1 year
            cert.set_issuer(ca_cert.get_subject())
            cert.set_pubkey(key)

            san = f"DNS:{common_name}".encode()
            cert.add_extensions([
                crypto.X509Extension(b"subjectAltName", False, san),
                crypto.X509Extension(b"extendedKeyUsage", False, b"serverAuth"),
            ])

            cert.sign(ca_key, 'sha256')
            return cert, key
        except Exception as e:
            log(f"Memory certificate generation failed: {e}")
            return None, None

    @staticmethod
    def generate_self_signed(
        cert_path: str = None,
        key_path: str = None,
        common_name: str = "debug.local"
    ) -> bool:
        """Generates a self-signed cert (signed by Root CA) saved to disk"""
        CertificateHelper._ensure_ca_dir()
        cert_path = cert_path or config.CERT_FILE
        key_path = key_path or config.KEY_FILE

        try:
            ca_cert, ca_key = CertificateHelper._ensure_ca()
            
            key = crypto.PKey()
            key.generate_key(crypto.TYPE_RSA, 2048)

            cert = crypto.X509()
            cert.set_version(2)
            cert.get_subject().CN = common_name
            cert.set_serial_number(int(time.time() * 100))
            cert.gmtime_adj_notBefore(0)
            cert.gmtime_adj_notAfter(365 * 24 * 60 * 60) # 1 year
            cert.set_issuer(ca_cert.get_subject())
            cert.set_pubkey(key)

            san = f"DNS:{common_name}".encode()
            cert.add_extensions([
                crypto.X509Extension(b"subjectAltName", False, san),
                crypto.X509Extension(b"extendedKeyUsage", False, b"serverAuth"),
            ])

            cert.sign(ca_key, 'sha256')

            with open(cert_path, "wb") as f:
                f.write(crypto.dump_certificate(crypto.FILETYPE_PEM, cert))
            with open(key_path, "wb") as f:
                f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, key))
            
            return True
        except Exception as e:
            log(f"Certificate generation failed: {e}")
            return False
