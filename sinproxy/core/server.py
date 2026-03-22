import socket
import ssl
import threading
import time
from sinproxy import config
from sinproxy.utils.logger import log
from sinproxy.security.certs import CertificateHelper

class DebugHTTPServer:
    """Professional SSL Debug Server for SNI testing"""
    def __init__(
        self,
        host: str = None,
        port: int = None,
        cert_file: str = None,
        key_file: str = None
    ):
        self.host = host or config.DEBUG_HOST
        self.port = port or config.DEBUG_PORT
        self.cert_file = cert_file or config.CERT_FILE
        self.key_file = key_file or config.KEY_FILE
        self.running = False
        self.thread: threading.Thread = None

    def start(self) -> bool:
        if not CertificateHelper.generate_self_signed(self.cert_file, self.key_file):
            return False

        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        try:
            # Universal TLS Support (1.0 - 1.3)
            try:
                context.minimum_version = ssl.TLSVersion.MINIMUM_SUPPORTED
                context.maximum_version = ssl.TLSVersion.MAXIMUM_SUPPORTED
            except:
                pass
            context.load_cert_chain(certfile=self.cert_file, keyfile=self.key_file)
        except Exception as e:
            log(f"Failed to load certificate: {e}")
            return False

        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        try:
            server_socket.bind((self.host, self.port))
            server_socket.listen(5)
            server_socket.settimeout(1.0) # Critical for Ctrl+C on Windows
        except Exception as e:
            log(f"Bind/listen failed: {e}")
            return False

        log(f"Debug HTTPS server listening on https://{self.host}:{self.port}")

        self.running = True
        self.thread = threading.Thread(target=self._serve_forever, args=(server_socket, context), daemon=True)
        self.thread.start()
        return True

    def stop(self):
        self.running = False

    def _serve_forever(self, sock: socket.socket, context: ssl.SSLContext):
        while self.running:
            try:
                client_sock, addr = sock.accept()
                log(f"← Connection from {addr[0]}:{addr[1]}")

                secure_sock = context.wrap_socket(client_sock, server_side=True)
                
                # SNI might not be available directly on the socket in all Python versions
                sni_str = "unknown"
                try:
                    if hasattr(secure_sock, 'get_servername'):
                        sni = secure_sock.get_servername()
                        sni_str = sni.decode(errors='replace') if sni else "not sent"
                    elif hasattr(secure_sock, 'server_hostname'):
                        sni_str = str(secure_sock.server_hostname)
                except:
                    pass

                log(f"  SNI received: {sni_str}")
                log(f"  TLS version: {secure_sock.version() or 'unknown'}")
                log(f"  Cipher: {secure_sock.cipher()[0] if secure_sock.cipher() else 'unknown'}")

                # Read request (very basic)
                try:
                    data = secure_sock.recv(4096)
                    if data:
                        lines = data.decode('utf-8', errors='replace').splitlines()
                        for line in lines[:12]:
                            if line.strip():
                                log(f"  {line}")
                except:
                    log("  Could not decode request")

                # Dummy response
                resp = (
                    "HTTP/1.1 200 OK\r\n"
                    "Content-Type: text/plain\r\n"
                    "Connection: close\r\n\r\n"
                    f"Debug server received your request.\n"
                    f"SNI was: {sni_str}\n"
                ).encode()

                secure_sock.sendall(resp)
                secure_sock.close()

            except socket.timeout:
                continue
            except (KeyboardInterrupt, SystemExit):
                self.running = False
                break
            except Exception as e:
                if self.running:
                    log(f"Server loop error: {e}")
                break

        sock.close()
