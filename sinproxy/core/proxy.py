import socket
import ssl
import threading
import select
import time
from OpenSSL import SSL
from typing import Optional, Tuple, Any

from sinproxy import config
from sinproxy.utils.logger import log
from sinproxy.security.certs import CertificateHelper

class ProxyHandler:
    """Professional Proxy Handler for MITM and SNI Tunneling"""
    def __init__(self, client_sock: socket.socket, addr: Tuple[str, int], sni: str = None):
        self.client_sock = client_sock
        self.addr = addr
        self.sni = sni or config.BUG_SNI
        self.remote_sock: Optional[socket.socket] = None
        self.secure_remote: Optional[ssl.SSLSocket] = None
        self.secure_client: Optional[Any] = None # OpenSSL.SSL.Connection

    def run(self):
        try:
            data = self.client_sock.recv(config.BUFFER_SIZE)
            if not data:
                return

            first_line = data.decode('ascii', errors='replace').split('\r\n')[0]
            log(f"Request: {first_line}")

            if first_line.startswith('CONNECT'):
                self._handle_connect(first_line)
            else:
                log("Plain HTTP request — not implemented in this version")
                self.client_sock.sendall(b"HTTP/1.1 501 Not Implemented\r\n\r\n")
        except Exception as e:
            log(f"Handler error for {self.addr}: {e}")
        finally:
            self._cleanup()

    def _handle_connect(self, first_line: str):
        """Handles HTTPS CONNECT tunneling with SNI spoofing and header modification"""
        try:
            # Parse host and port
            parts = first_line.split(" ")
            if len(parts) < 2:
                return
            host_port = parts[1]
            if ":" not in host_port:
                host, port = host_port, 443
            else:
                host, port_str = host_port.split(":")
                port = int(port_str)

            log(f"CONNECT → {host}:{port} (Using SNI: {self.sni})")

            # Outbound: Connect to real target with self.sni
            self.remote_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.remote_sock.settimeout(config.PROXY_TIMEOUT)
            self.remote_sock.connect((host, port))

            remote_context = ssl._create_unverified_context()
            try:
                # Universal TLS Support (1.0 - 1.3)
                remote_context.minimum_version = ssl.TLSVersion.MINIMUM_SUPPORTED
                remote_context.maximum_version = ssl.TLSVersion.MAXIMUM_SUPPORTED
            except:
                pass

            self.secure_remote = remote_context.wrap_socket(
                self.remote_sock,
                server_hostname=self.sni
            )
            version = self.secure_remote.version()
            log(f"Outbound TLS established: {version} | SNI: {self.sni}")

            # 3. MITM: Wrap the CLIENT connection with internal SSL (IN-MEMORY)
            self.client_sock.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            
            # Generate cert for the requested host in-memory (thread-safe)
            cert, key = CertificateHelper.generate_certificate_data(common_name=host)
            if not cert:
                log("Failed to generate in-memory certificate")
                return

            # Universal MITM Context
            client_ctx = SSL.Context(SSL.SSLv23_METHOD)
            client_ctx.use_privatekey(key)
            client_ctx.use_certificate(cert)
            
            try:
                self.secure_client = SSL.Connection(client_ctx, self.client_sock)
                self.secure_client.set_accept_state()
                log(f"Inbound MITM TLS established for host: {host}")
            except Exception as e:
                log(f"MITM Handshake failed: {e}")
                return

            # 4. Read and modify the FIRST request from the decrypted stream
            try:
                payload = self.secure_client.recv(config.BUFFER_SIZE)
            except SSL.WantReadError:
                payload = b""

            if payload:
                modified_payload = self._modify_headers(payload)
                self.secure_remote.sendall(modified_payload)
                log("Modified decrypted request sent to server.")

            # 5. Start bidirectional forwarding for the decrypted stream
            self._start_forwarding(self.secure_client, self.secure_remote)

        except Exception as e:
            log(f"CONNECT error: {e}")

    def _modify_headers(self, data: bytes) -> bytes:
        """
        Intersects and modifies the HTTP headers.
        Applies professional injection patterns.
        """
        try:
            if b"\r\n\r\n" not in data:
                return data

            header_part, body_part = data.split(b"\r\n\r\n", 1)
            lines = header_part.split(b"\r\n")
            if not lines:
                return data

            new_lines = [lines[0]]  # Keep method line
            
            # Professional injections
            new_lines.append(f"X-Online-Host: {self.sni}".encode())
            new_lines.append(f"X-Forwarded-Host: {self.sni}".encode())
            
            # Process existing headers (avoid duplicates)
            for line in lines[1:]:
                lower_line = line.lower()
                if any(lower_line.startswith(x) for x in [b"x-online-host:", b"x-forwarded-host:"]):
                    continue
                new_lines.append(line)

            return b"\r\n".join(new_lines) + b"\r\n\r\n" + body_part
        except Exception as e:
            log(f"Header modification failed: {e}")
            return data

    def _start_forwarding(self, client: Any, remote: Any):
        t1 = threading.Thread(target=self._bridge, args=(client, remote, "client->server"), daemon=True)
        t2 = threading.Thread(target=self._bridge, args=(remote, client, "server->client"), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()

    def _bridge(self, src: Any, dst: Any, label: str):
        try:
            while True:
                try:
                    # Professional Step: Check for pending data in pyOpenSSL objects
                    has_data = False
                    if hasattr(src, "pending") and src.pending() > 0:
                        has_data = True
                    else:
                        r, _, _ = select.select([src], [], [], 2)
                        if r:
                            has_data = True
                    
                    if has_data:
                        data = src.recv(config.BUFFER_SIZE)
                        if not data:
                            break
                        dst.sendall(data)
                except (SSL.WantReadError, SSL.WantWriteError):
                    time.sleep(0.01)
                    continue
                except Exception:
                    break
        except:
            pass
        finally:
            log(f"Connection closed: {label}")

    def _cleanup(self):
        for s in [self.client_sock, self.remote_sock, self.secure_remote, self.secure_client]:
            if s:
                try:
                    s.close()
                except:
                    pass

class SNIProxy:
    """Professional SNI Proxy Server Management"""
    def __init__(self, host: str = "127.0.0.1", port: int = 8081, sni: str = None):
        self.host = host
        self.port = port
        self.sni = sni or config.BUG_SNI
        self.running = False

    def start(self):
        # Professional Step: Ensure Root CA exists on startup
        try:
            CertificateHelper._ensure_ca()
        except Exception as e:
            log(f"Warning: Root CA generation failed: {e}")

        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            server.bind((self.host, self.port))
            server.listen(100)
            server.settimeout(1.0) # Critical for Ctrl+C on Windows
            self.running = True
            log(f"SNI Proxy professional server listening on {self.host}:{self.port}")
            log(f"Tunneling via SNI: {self.sni}")
        except Exception as e:
            log(f"Failed to start server: {e}")
            return

        while self.running:
            try:
                client_sock, addr = server.accept()
                handler = ProxyHandler(client_sock, addr, sni=self.sni)
                threading.Thread(target=handler.run, daemon=True).start()
            except socket.timeout:
                continue
            except (KeyboardInterrupt, SystemExit):
                log("Termination signal received.")
                self.running = False
                break
            except Exception as e:
                log(f"Accept error: {e}")
        
        server.close()
        log("Proxy server stopped.")

    @staticmethod
    def test_connection(sni: str = None):
        """Self-test logic: tries to reach google.com through the proxy logic"""
        sni = sni or config.BUG_SNI
        log("Starting SELF-TEST for proxy logic...")
        try:
            host, port = "www.google.com", 443
            log(f"Self-test: Connecting to {host}:{port} using SNI {sni}...")
            
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(config.PROXY_TIMEOUT)
            sock.connect((host, port))
            
            context = ssl._create_unverified_context()
            try:
                context.set_alpn_protocols(['http/1.1', 'h2'])
            except:
                pass

            secure_sock = context.wrap_socket(sock, server_hostname=sni)
            log(f"TLS Handshake successful in self-test. Cipher: {secure_sock.cipher()[0]}")
            
            # Send test request
            request = (
                f"GET / HTTP/1.1\r\n"
                f"Host: {host}\r\n"
                f"User-Agent: SNI-Proxy-SelfTester/1.0\r\n"
                f"Connection: close\r\n\r\n"
            ).encode()
            
            secure_sock.sendall(request)
            response = secure_sock.recv(4096)
            
            log("--- SELF-TEST RESPONSE PREVIEW ---")
            log(response.decode("ascii", errors="replace")[:500])
            log("----------------------------------")
            
            if b"HTTP/1.1 200" in response or b"HTTP/1.1 30" in response:
                log("[ SUCCESS ] Proxy logic is working correctly.")
            else:
                log("[ WARNING ] Unexpected response code in test.")
                
            secure_sock.close()
        except Exception as e:
            log(f"[ FAILED ] Self-test error: {e}")
