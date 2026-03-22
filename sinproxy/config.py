#!/usr/bin/env python3
"""
Central configuration for SINproxy project.
"""
import os

# --- Project Base Directory ---
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --- Proxy Settings ---
PROXY_HOST = "127.0.0.1"
PROXY_PORT = 8081
PROXY_TIMEOUT = 15
BUFFER_SIZE = 65535
# TLS_MIN_VERSION: Set to TLSv1 for maximum compatibility (1.0, 1.1, 1.2, 1.3)
TLS_MIN_VERSION = "TLSv1" 

# --- Bug SNI Settings ---
# This is the "magic" host used for TLS handshake
BUG_SNI = "telegram.org"

# --- Dynamic Overrides from Root ---
_root_config_path = os.path.join(BASE_DIR, "config.txt")
if os.path.exists(_root_config_path):
    try:
        with open(_root_config_path, "r") as f:
            _line = f.read().strip()
            if _line:
                BUG_SNI = _line
    except:
        pass

# --- Debug Server Settings ---
DEBUG_HOST = "127.0.0.1"
DEBUG_PORT = 8443
CERT_FILE = os.path.join(BASE_DIR, "certs", "debug_server.crt")
KEY_FILE = os.path.join(BASE_DIR, "certs", "debug_server.key")
ROOT_CA_CRT = os.path.join(BASE_DIR, "certs", "rootCA.crt")
ROOT_CA_KEY = os.path.join(BASE_DIR, "certs", "rootCA.key")

# --- Target Settings (for SIN.py testing) ---
REAL_TARGET = "api.myip.com"
REAL_PORT = 443
REAL_PATH = "/"
VERIFY_SSL = False
