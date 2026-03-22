import argparse
import time
import sys
from sinproxy import config
from sinproxy.utils.logger import log
from sinproxy.core.proxy import SNIProxy
from sinproxy.core.server import DebugHTTPServer
from sinproxy.core.client import SNIClient

def main():
    parser = argparse.ArgumentParser(description="SINproxy - Modern SNI Proxy & Tester")
    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Proxy Command
    proxy_parser = subparsers.add_parser("proxy", help="Start the SNI Proxy server")
    proxy_parser.add_argument("--host", default=config.PROXY_HOST, help=f"Host to bind (default: {config.PROXY_HOST})")
    proxy_parser.add_argument("--port", type=int, default=config.PROXY_PORT, help=f"Port to bind (default: {config.PROXY_PORT})")
    proxy_parser.add_argument("--sni", default=config.BUG_SNI, help=f"SNI to use (default: {config.BUG_SNI})")
    proxy_parser.add_argument("--test", action="store_true", help="Run a self-test before starting")

    # Server Command
    server_parser = subparsers.add_parser("server", help="Start the Debug HTTPS server")
    server_parser.add_argument("--host", default=config.DEBUG_HOST, help=f"Host to bind (default: {config.DEBUG_HOST})")
    server_parser.add_argument("--port", type=int, default=config.DEBUG_PORT, help=f"Port to bind (default: {config.DEBUG_PORT})")

    # Client Command
    client_parser = subparsers.add_parser("client", help="Run the SNI connection tester")
    client_parser.add_argument("--target", default=config.REAL_TARGET, help=f"Target host (default: {config.REAL_TARGET})")
    client_parser.add_argument("--port", type=int, default=config.REAL_PORT, help=f"Target port (default: {config.REAL_PORT})")
    client_parser.add_argument("--sni", default=config.BUG_SNI, help=f"SNI to use (default: {config.BUG_SNI})")

    # Suite Command (Combined Test)
    suite_parser = subparsers.add_parser("suite", help="Run the full SNI test suite")
    suite_parser.add_argument("--sni", default=config.BUG_SNI, help=f"SNI to use (default: {config.BUG_SNI})")

    args = parser.parse_args()

    if args.command == "proxy":
        proxy = SNIProxy(args.host, args.port, sni=args.sni)
        if args.test:
            proxy.test_connection(sni=args.sni)
        proxy.start()

    elif args.command == "server":
        server = DebugHTTPServer(args.host, args.port)
        if server.start():
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                log("Stopping debug server...")
                server.stop()

    elif args.command == "client":
        client = SNIClient(target_host=args.target, target_port=args.port, sni=args.sni)
        client.run()

    elif args.command == "suite":
        run_suite(args.sni)

    else:
        parser.print_help()

def run_suite(sni: str = None):
    log("Running SNI Bug Test Suite")
    sni = sni or config.BUG_SNI
    
    # 1. Start debug server
    server = DebugHTTPServer()
    if not server.start():
        log("Debug server failed to start")
        return

    time.sleep(1.5)

    # 2. Test local debug server
    log("\n" + "═"*75)
    log("TEST 1 : Local debug server")
    local_test = SNIClient(
        target_host=config.DEBUG_HOST,
        target_port=config.DEBUG_PORT,
        sni=sni,
        verify_ssl=False
    )
    local_test.run()

    # 3. Test real target
    log("\n" + "═"*75)
    log(f"TEST 2 : Real target via bug SNI ({sni})")
    real_test = SNIClient(sni=sni)
    real_test.run()

    log("\n" + "═"*75)
    log("All tests finished. Press Ctrl+C to stop debug server...")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        server.stop()

if __name__ == "__main__":
    main()
