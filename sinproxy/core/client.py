import socket
import ssl
import time
from sinproxy import config
from sinproxy.utils.logger import log

class SNIClient:
    """Professional SNI Client for testing bug hosts"""
    def __init__(
        self,
        target_host: str = None,
        target_port: int = None,
        sni: str = None,
        path: str = None,
        verify_ssl: bool = None,
        timeout: int = 12
    ):
        self.target_host = target_host or config.REAL_TARGET
        self.target_port = target_port or config.REAL_PORT
        self.sni = sni or config.BUG_SNI
        self.path = path or config.REAL_PATH
        self.verify_ssl = verify_ssl if verify_ssl is not None else config.VERIFY_SSL
        self.timeout = timeout

    def run(self) -> bool:
        log(f"Testing → {self.target_host}:{self.target_port}  (SNI: {self.sni})")

        try:
            target_ip = socket.gethostbyname(self.target_host)
            log(f"Resolved {self.target_host} → {target_ip}")
        except Exception as e:
            log(f"DNS resolution failed: {e}")
            return False

        ssl_context = ssl.create_default_context() if self.verify_ssl else ssl._create_unverified_context()
        
        # Set Modern TLS Version and Ciphers for better compatibility (Cloudflare, etc.)
        try:
            ssl_context.minimum_version = ssl.TLSVersion.TLSv1_2
            ssl_context.maximum_version = ssl.TLSVersion.TLSv1_3
            
            # Use a more "browser-like" cipher suite
            ssl_context.set_ciphers('ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384')
            
            # Set ALPN protocols (critical for some servers)
            ssl_context.set_alpn_protocols(['http/1.1', 'h2'])
        except Exception as e:
            log(f"TLS context refinement warning: {e}")

        try:
            with socket.create_connection((target_ip, self.target_port), timeout=self.timeout) as sock:
                with ssl_context.wrap_socket(sock, server_hostname=self.sni) as secure_sock:
                    # Log security details
                    version = secure_sock.version()
                    cipher = secure_sock.cipher()[0]
                    alpn = secure_sock.selected_alpn_protocol()
                    log(f"TLS handshake OK → {version}, {cipher} | ALPN: {alpn}")

                    request = (
                        f"GET {self.path} HTTP/1.1\r\n"
                        f"Host: {self.target_host}\r\n"
                        f"User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36\r\n"
                        f"Accept: */*\r\n"
                        f"Connection: close\r\n\r\n"
                    ).encode()

                    secure_sock.sendall(request)

                    response = b""
                    while True:
                        chunk = secure_sock.recv(8192)
                        if not chunk:
                            break
                        response += chunk

                    text = response.decode('utf-8', errors='replace')
                    head = text[:1400].replace('\r\n', '\n').strip()

                    log("Response preview (first ~1400 chars):")
                    log("\n" + head)
                    log("─" * 70)

                    if "200 OK" in text[:300]:
                        log("[ SUCCESS ] Status 200 OK")
                        return True
                    elif any(x in text[:300] for x in ["301", "302", "307", "308"]):
                        log("[ REDIRECT ] Got HTTP redirect")
                    else:
                        log("[ BLOCKED / FILTERED / ERROR ] Unexpected response")

                    return False

        except ssl.SSLError as e:
            log(f"SSL error: {e}")
        except socket.timeout:
            log("Timeout — no response")
        except ConnectionResetError:
            log("Connection reset by peer")
        except Exception as e:
            log(f"Connection failed → {type(e).__name__}: {e}")

        return False
